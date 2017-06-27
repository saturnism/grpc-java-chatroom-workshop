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

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.grpc.*;

/**
 * Created by rayt on 10/6/16.
 */
public class JwtServerInterceptor implements ServerInterceptor {
  private static final ServerCall.Listener NOOP_LISTENER = new ServerCall.Listener() {
  };

  private final JWTVerifier verifier;

  public JwtServerInterceptor(String issuer, Algorithm algorithm) {
    this.verifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build();
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
    String token = metadata.get(Constant.JWT_METADATA_KEY);
    if (token == null) {
      serverCall.close(Status.UNAUTHENTICATED.withDescription("JWT Token is missing from Metadata"), metadata);
      return NOOP_LISTENER;
    }

    try {
      DecodedJWT jwt = verifier.verify(token);
      Context ctx = Context.current()
          .withValue(Constant.USER_ID_CTX_KEY, jwt.getSubject())
          .withValue(Constant.JWT_CTX_KEY, jwt);
      return Contexts.interceptCall(ctx, serverCall, metadata, serverCallHandler);
    } catch (Exception e) {
      System.out.println("Verification failed - Unauthenticated!");
      serverCall.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e), metadata);
      return NOOP_LISTENER;
    }
  }
}
