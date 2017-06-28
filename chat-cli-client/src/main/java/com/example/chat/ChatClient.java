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

import brave.grpc.GrpcTracing;
import com.example.auth.*;
import com.example.chat.grpc.ClientGrpcUtils;
import io.grpc.stub.StreamObserver;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.logging.Logger;


/**
 * Created by rayt on 6/27/17.
 */
public class ChatClient {
  private static Logger logger = Logger.getLogger(ChatClient.class.getName());


  public static void main(String[] args) throws Exception {
    (new ChatClient()).runStartingPrompt();
    System.exit(1);
  }

  public void runStartingPrompt() throws Exception {

    GrpcTracing tracing = ClientGrpcUtils.createTracing();
    AuthenticationServiceGrpc.AuthenticationServiceBlockingStub authService = ClientGrpcUtils.createAuthChannel(tracing);

    String startOptions = "login [username] | create [username] | quit\n->";

    Terminal terminal = TerminalBuilder.terminal();
    PrintWriter out = terminal.writer();
// StringsCompleter stringsCompleter = new StringsCompleter("/quit", "/join", "/leave");

    LineReader lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .build();


    String nextLine;
    System.out.println("Press ctrl+D to quit");
    while (true) {
      try {
        nextLine = lineReader.readLine(startOptions);
        String[] splitLine = nextLine.split(" ");
        String command = splitLine[0];
        logger.info("processing " + command);
        if (splitLine.length >= 2) {
          String username = splitLine[1];
          if (command.equalsIgnoreCase("create")) {
            out.println("creating user not implemented");
            //createUser(username, lineReader, authService);
          } else if (command.equalsIgnoreCase("login")) {
            out.println("processing login user");
            login(username, lineReader, authService, tracing);
          }
        } else if (command.equalsIgnoreCase("quit")) {
          break;
        }
      } catch (EndOfFileException e) {
        System.exit(1);
      }
    }
  }

  //create is not implemented yet
  public void createUser(String username,
                         LineReader reader,
                         AuthenticationServiceGrpc.AuthenticationServiceBlockingStub authService) throws Exception {
    String password = reader.readLine("password> ", '*');
    String roles = reader.readLine("roles (admin, user)> ");
    //TODO - add create user to auth service
  }

  private class CurrentClientState {
    StreamObserver<ChatMessage> observer = null;
    String room = null;

    public CurrentClientState(StreamObserver<ChatMessage> observer, String room) {
      this.observer = observer;
      this.room = room;
    }
  }

  public void login(String username,
                    LineReader lineReader,
                    AuthenticationServiceGrpc.AuthenticationServiceBlockingStub authService,
                    GrpcTracing tracing) throws IOException {
    String password = lineReader.readLine("password> ", '*');

    logger.info("authenticated user: " + username);
    AuthenticationResponse authenticationReponse = authService.authenticate(AuthenticationRequest.newBuilder()
            .setUsername(username)
            .setPassword(password)
            .build());

    String token = authenticationReponse.getToken();

    AuthorizationResponse authorizationResponse = authService.authorization(AuthorizationRequest.newBuilder()
            .setToken(token)
            .build());

    //TODO - handle failed login and report to the user a failed login attempt
    logger.info(authorizationResponse.toString());

    //this is the room service for creating, listing rooms, etc.
    ChatRoomServiceGrpc.ChatRoomServiceBlockingStub chatRoomService = ClientGrpcUtils.createChatRoomService(token, tracing);

    //this is the service for sending and recieving messages.
    ChatStreamServiceGrpc.ChatStreamServiceStub chatStreamService = ClientGrpcUtils.createChatStreamService(token, tracing);

    CurrentClientState currentClientState = null;
    String prompt = "[chat message] | /join [room] | /leave [room] | /create [room] | /list | /quit\n" + username + "->";

    while (true) {
      //managing state with currentClientState seems wrong - wonder if there is a better way?
      currentClientState = processCommandLine(username, lineReader, chatRoomService, chatStreamService, currentClientState, prompt);

    }
  }

  private CurrentClientState processCommandLine(String username, LineReader lineReader,
                                                ChatRoomServiceGrpc.ChatRoomServiceBlockingStub chatRoomService,
                                                ChatStreamServiceGrpc.ChatStreamServiceStub chatStreamService,
                                                CurrentClientState currentClientState, String prompt) {
    try {
      String line = lineReader.readLine(prompt);
      if (line.startsWith("/")) {
        currentClientState = processChatCommands(username, chatRoomService, chatStreamService, currentClientState, line);
      } else if (!line.isEmpty()) {
        //if the line was not a chat command then send it as a message to the other rooms
        if ((currentClientState == null) || (currentClientState.observer == null)) {
          logger.info("error - not in a room");
        } else {
          logger.info("sending chat message");
          currentClientState.observer.onNext(ChatMessage.newBuilder()
                  .setType(MessageType.TEXT)
                  .setRoomName(currentClientState.room)
                  .setMessage(line)
                  .build());
        }
      }
    } catch (UserInterruptException e) {
    } catch (EndOfFileException e) {
      System.exit(1);
    }
    return currentClientState;
  }

  private CurrentClientState processChatCommands(String username, ChatRoomServiceGrpc.ChatRoomServiceBlockingStub chatRoomService,
                                                 ChatStreamServiceGrpc.ChatStreamServiceStub chatStreamService,
                                                 CurrentClientState currentClientState, String line) {

    if ("/quit".equalsIgnoreCase(line) || "/exit".equalsIgnoreCase(line)) {
      logger.info("Exiting chat client");
      System.exit(1);
    } else if ("/list".equalsIgnoreCase(line) || "/exit".equalsIgnoreCase(line)) {
      logger.info("listing rooms");
      Iterator<Room> rooms = chatRoomService.getRooms(Empty.getDefaultInstance());
      rooms.forEachRemaining(r -> logger.info("Room: " + r.getName()));
    }
    //process leave room
    else if ("/leave".equalsIgnoreCase(line)) {
      if ((currentClientState == null) || (currentClientState.observer == null)) {
        logger.info("error - not in a room");
      } else {
        currentClientState.observer.onNext(ChatMessage.newBuilder()
                .setType(MessageType.JOIN)
                .setRoomName(currentClientState.room)
                .setMessage(line)
                .build());
        //TODO - what else do we need to do when the user leaves a room?
        currentClientState = null;
      }
    }
    //process commands that take a room name like join and create
    else {
      String[] splitLine = line.split(" ");
      if (splitLine.length == 2) {
        String command = splitLine[0];
        String room = splitLine[1];
        currentClientState = processRoomCommand(username, chatRoomService, chatStreamService, currentClientState, command, room);
      }
    }
    return currentClientState;
  }

  private CurrentClientState processRoomCommand(String username,
                                                ChatRoomServiceGrpc.ChatRoomServiceBlockingStub chatRoomService,
                                                ChatStreamServiceGrpc.ChatStreamServiceStub chatStreamService,
                                                CurrentClientState currentClientState,
                                                String command, String room) {

    if ("/join".equalsIgnoreCase(command)) {
      StreamObserver<ChatMessage> observer = ClientGrpcUtils.createChatStreamObserver(chatStreamService, username, room);
      observer.onNext(ChatMessage.newBuilder()
              .setType(MessageType.JOIN)
              .setRoomName(room)
              .setMessage("joining room")
              .build());

      currentClientState = new CurrentClientState(observer, room);
      logger.info("joined room");
    }
    else if ("/create".equalsIgnoreCase(command)) {
      chatRoomService.createRoom(Room.newBuilder()
              .setName(room)
              .build());
      logger.info("created new room");
    }
    return currentClientState;
  }


}
