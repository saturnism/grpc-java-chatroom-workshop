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

package com.example.auth;

import brave.Tracing;
import brave.grpc.GrpcTracing;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.auth.domain.User;
import com.example.auth.grpc.AuthServiceImpl;
import com.example.auth.grpc.ClientIdServerInterceptor;
import com.example.auth.repository.UserRepository;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.urlconnection.URLConnectionSender;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Created by rayt on 6/27/17.
 */
public class AuthServer {
  private static Logger logger = Logger.getLogger(AuthServer.class.getName());

  private static UserRepository createRepository() {
    return new UserRepository();

  }

  public static void main(String[] args) throws IOException, InterruptedException {
    final AsyncReporter<Span> reporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v1/spans"));
    final GrpcTracing tracing = GrpcTracing.create(Tracing.newBuilder()
        .localServiceName("auth-service")
        .reporter(reporter)
        .build());
    final UserRepository repository = createRepository();
    final Algorithm algorithm = Algorithm.HMAC256("secret");
    final AuthServiceImpl authService = new AuthServiceImpl(repository, "auth-issuer", algorithm);
    final ClientIdServerInterceptor clientIdServerInterceptor = new ClientIdServerInterceptor();

    final Server server = ServerBuilder.forPort(9091)
        .addService(ServerInterceptors.intercept(authService, clientIdServerInterceptor, tracing.newServerInterceptor()))
        .build();

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        server.shutdownNow();
      }
    });
    server.start();

    logger.info("Server started on port 9091");

    server.awaitTermination();

  }
}
