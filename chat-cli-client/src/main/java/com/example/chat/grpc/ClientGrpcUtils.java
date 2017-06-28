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

package com.example.chat.grpc;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import com.example.auth.AuthenticationServiceGrpc;
import com.example.auth.grpc.*;
import com.example.chat.*;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.urlconnection.URLConnectionSender;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ryknight on 6/28/17.
 */
public class ClientGrpcUtils {

  private static Logger logger = Logger.getLogger(ChatClient.class.getName());

  private static String AUTH_SERVER = "localhost:9091";
  private static String CHAT_SERVER = "localhost:9092";

  /**
   * This adds a callback to the chat stream to watch for new messages being pushed from the server.
   * @param chatStreamService
   * @param username
   * @param room
   * @return
   */
  public static StreamObserver<ChatMessage> createChatStreamObserver(ChatStreamServiceGrpc.ChatStreamServiceStub chatStreamService,
                                                                     String username, String room) {
    return chatStreamService.chat(new StreamObserver<ChatMessageFromServer>() {
      @Override
      public void onNext(ChatMessageFromServer chatMessageFromServer) {
        //If the message is for the same room that we are logged into and the message is not from ourselves then print it to the console
        //TODO - do we need to check for room?  We should get messages destined for other rooms?
        String roomName = chatMessageFromServer.getRoomName();
        logger.info("processing message for room: " + room);
        if (roomName.equalsIgnoreCase(room) && !chatMessageFromServer.getFrom().equalsIgnoreCase(username))
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
  }

  public static GrpcTracing createTracing() {
    final AsyncReporter<Span> reporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v1/spans"));
    final GrpcTracing tracing = GrpcTracing.create(Tracing.newBuilder()
            .localServiceName("chat-client")
            .reporter(reporter)
            .build());

    return tracing;
  }

  protected static ManagedChannel createManagedChannel(GrpcTracing tracing, String server) {

    final Metadata headers = new Metadata();
    headers.put(com.example.auth.grpc.Constant.CLIENT_ID_METADATA_KEY, "chat-cli-client");

    ClientInterceptor headersInterceptor = MetadataUtils.newAttachHeadersInterceptor(headers);

    final ManagedChannel managedChannel = ManagedChannelBuilder.forTarget(server)
            .usePlaintext(true)
            .intercept(headersInterceptor, tracing.newClientInterceptor())
            .build();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        managedChannel.shutdownNow();
      }
    });
    return managedChannel;
  }


  public static AuthenticationServiceGrpc.AuthenticationServiceBlockingStub createAuthChannel(GrpcTracing tracing) {
    final ManagedChannel authChannel = createManagedChannel(tracing, AUTH_SERVER);
    return AuthenticationServiceGrpc.newBlockingStub(authChannel);
  }

  public static ChatStreamServiceGrpc.ChatStreamServiceStub createChatStreamService(String token, GrpcTracing tracing) {
    final ManagedChannel chatChannel = createManagedChannel(tracing, CHAT_SERVER);
    return ChatStreamServiceGrpc.newStub(chatChannel)
            .withCallCredentials(new JwtCallCredential(token));
  }

  public static ChatRoomServiceGrpc.ChatRoomServiceBlockingStub createChatRoomService(String token, GrpcTracing tracing) {
    final ManagedChannel chatChannel = createManagedChannel(tracing, CHAT_SERVER);
    ChatRoomServiceGrpc.ChatRoomServiceBlockingStub chatRoomService = ChatRoomServiceGrpc.newBlockingStub(chatChannel)
            .withCallCredentials(new JwtCallCredential(token));
    return chatRoomService;
  }
}
