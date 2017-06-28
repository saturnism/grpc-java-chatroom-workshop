/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.chat;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import com.example.auth.*;
import com.example.chat.grpc.JwtCallCredential;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.urlconnection.URLConnectionSender;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by rayt on 6/27/17.
 */
public class ChatClient {
  private static Logger logger = Logger.getLogger(ChatClient.class.getName());

  public static void main(String[] args) throws IOException {
    final AsyncReporter<Span> reporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v1/spans"));
    final GrpcTracing tracing = GrpcTracing.create(Tracing.newBuilder()
        .localServiceName("chat-client")
        .reporter(reporter)
        .build());

    final ManagedChannel authChannel = ManagedChannelBuilder.forTarget("localhost:9091")
        .usePlaintext(true)
        .intercept(tracing.newClientInterceptor())
        .build();
    AuthenticationServiceGrpc.AuthenticationServiceBlockingStub authService = AuthenticationServiceGrpc.newBlockingStub(authChannel);

    AuthenticationResponse authenticationReponse = authService.authenticate(AuthenticationRequest.newBuilder()
        .setUsername("admin")
        .setPassword("qwerty")
        .build());

    String token = authenticationReponse.getToken();

    AuthorizationResponse authorizationResponse = authService.authorization(AuthorizationRequest.newBuilder()
        .setToken(token)
        .build());

    logger.info(authorizationResponse.toString());

    final ManagedChannel chatChannel = ManagedChannelBuilder.forTarget("localhost:9092")
        .usePlaintext(true)
        .intercept(tracing.newClientInterceptor())
        .build();

    ChatRoomServiceGrpc.ChatRoomServiceBlockingStub chatRoomService = ChatRoomServiceGrpc.newBlockingStub(chatChannel)
        .withCallCredentials(new JwtCallCredential(token));

    chatRoomService.createRoom(Room.newBuilder()
        .setName("grpc-dev")
        .build());
    chatRoomService.createRoom(Room.newBuilder()
        .setName("grpc-user")
        .build());

    Iterator<Room> rooms = chatRoomService.getRooms(Empty.getDefaultInstance());
    rooms.forEachRemaining(r -> logger.info("Room: " + r.getName()));

    ChatStreamServiceGrpc.ChatStreamServiceStub chatStreamService = ChatStreamServiceGrpc.newStub(chatChannel)
        .withCallCredentials(new JwtCallCredential(token));

    LineReader reader = LineReaderBuilder.builder().build();
    String prompt = "admin> ";

    StreamObserver<ChatMessage> observer = chatStreamService.chat(new StreamObserver<ChatMessageFromServer>() {
      @Override
      public void onNext(ChatMessageFromServer chatMessageFromServer) {
        System.out.println(String.format("\n%s> %s", chatMessageFromServer.getFrom(), chatMessageFromServer.getMessage()));
      }

      @Override
      public void onError(Throwable throwable) {
        logger.log(Level.SEVERE, "gRPC Error", throwable);
      }

      @Override
      public void onCompleted() {
      }
    });

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        authChannel.shutdownNow();
        chatChannel.shutdownNow();
      }
    });

    Terminal terminal = TerminalBuilder.terminal();
    LineReader lineReader = LineReaderBuilder.builder()
        .terminal(terminal)
        .build();

    while (true) {
      String line = null;
      try {
        line = reader.readLine(prompt);
        if (line.startsWith("/")) {
          if ("/quit".equalsIgnoreCase(line) || "/exit".equalsIgnoreCase(line)) {
            System.exit(1);
          }
        } else if (!line.isEmpty()) {
          observer.onNext(ChatMessage.newBuilder()
              .setRoomName("grpc-dev")
              .setMessage(line)
              .build());
        }
      } catch (UserInterruptException e) {
      } catch (EndOfFileException e) {
        System.exit(1);
      }
    }
  }
}
