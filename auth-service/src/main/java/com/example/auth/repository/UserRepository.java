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

package com.example.auth.repository;

import com.example.auth.domain.User;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by rayt on 6/27/17.
 */
public class UserRepository {
  private ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

  public User findUser(String username) {
    User user = users.get(username);
    if (user == null) return null;

    return user;
  }

  public Iterable<String> findRoles(String username) {
    User user = findUser(username);
    if (user == null) {
      return Collections.emptySet();
    }
    return user.getRoles();
  }

  public User save(User user) {
    User copy = new User(user);
    users.put(user.getUsername(), copy);
    return copy;
  }
}
