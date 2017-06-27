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

import com.example.chat.repository.ChatRoomRepository;
import com.google.protobuf.Timestamp;
import io.grpc.stub.StreamObserver;

import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by rayt on 6/27/17.
 */
public class ChatStreamServiceImpl extends ChatStreamServiceGrpc.ChatStreamServiceImplBase {
  private static final Logger logger = Logger.getLogger(ChatStreamServiceImpl.class.getName());
  private static Set<StreamObserver<ChatMessageFromServer>> observers =Collections.newSetFromMap(new ConcurrentHashMap<>());

  private final ChatRoomRepository repository;

  public ChatStreamServiceImpl(ChatRoomRepository repository) {
    this.repository = repository;
  }

  @Override
  public StreamObserver<ChatMessage> chat(StreamObserver<ChatMessageFromServer> responseObserver) {
    observers.add(responseObserver);

    return new StreamObserver<ChatMessage>() {
      @Override
      public void onNext(ChatMessage chatMessage) {
        String roomName = chatMessage.getRoomName();
        Room room = repository.findRoom(roomName);
        Timestamp now = Timestamp.newBuilder()
            .setSeconds(new Date().getTime())
            .build();

        if (room == null) {
          responseObserver.onNext(ChatMessageFromServer.newBuilder()
              .setTimestamp(now)
              .setMessage("Room does not exist: " + roomName)
          .build());
          return;
        }

        final ChatMessageFromServer messageFromServer = ChatMessageFromServer.newBuilder()
            .setTimestamp(now)
            .setRoomName(chatMessage.getRoomName())
            .setMessage(chatMessage.getMessage())
            .build();
        observers.stream().forEach(o -> o.onNext(messageFromServer));
      }

      @Override
      public void onError(Throwable throwable) {
        logger.log(Level.SEVERE, "Error in StreamObserver", throwable);
      }

      @Override
      public void onCompleted() {
        observers.remove(responseObserver);
      }
    };
  }
}
