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

import com.auth0.jwt.algorithms.Algorithm;
import com.example.auth.AuthenticationServiceGrpc;
import com.example.chat.repository.ChatRoomRepository;
import io.grpc.*;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Created by rayt on 6/27/17.
 */
public class ChatServer {
  private static final Logger logger = Logger.getLogger(ChatServer.class.getName());

  public static void main(String[] args) throws IOException, InterruptedException {
    ChatRoomRepository repository = new ChatRoomRepository();
    JwtServerInterceptor jwtServerInterceptor = new JwtServerInterceptor("chat-auth-issuer", Algorithm.HMAC256("secret"));

    final ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:9091")
        .usePlaintext(true)
        .build();

    final AuthenticationServiceGrpc.AuthenticationServiceBlockingStub authService = AuthenticationServiceGrpc.newBlockingStub(channel);

    final Server server = ServerBuilder.forPort(9092)
        .addService(ServerInterceptors.intercept(new ChatRoomServiceImpl(repository, authService), jwtServerInterceptor))
        .build();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        server.shutdownNow();
        channel.shutdownNow();
      }
    });

    server.start();
    logger.info("Server Started on port 9092");
    server.awaitTermination();
  }
}
