/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.session.clientside.internal;

import com.google.common.collect.Iterables;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.Cookie;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.ResponseMetaData;
import ratpack.session.clientside.SessionService;
import ratpack.session.store.SessionStorage;
import ratpack.session.store.internal.DefaultSessionStorage;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CookieBasedSessionStorageBindingHandler implements Handler {

  private final SessionService sessionService;

  private final String sessionName;
  private final Handler handler;

  public CookieBasedSessionStorageBindingHandler(SessionService sessionService, String sessionName, Handler handler) {
    this.sessionService = sessionService;
    this.sessionName = sessionName;
    this.handler = handler;
  }

  public void handle(final Context context) {
    context.getRequest().addLazy(SessionStorage.class, () -> {
      Cookie sessionCookie = Iterables.find(context.getRequest().getCookies(), c -> sessionName.equals(c.name()), null);
      ConcurrentMap<String, Object> sessionMap = sessionService.deserializeSession(sessionCookie);
      DefaultSessionStorage storage = new DefaultSessionStorage(sessionMap);
      ConcurrentMap<String, Object> initialSessionMap = new ConcurrentHashMap<>(sessionMap);
      context.getRequest().add(InitialStorageContainer.class, new InitialStorageContainer(new DefaultSessionStorage(initialSessionMap)));
      return storage;
    });

    context.getResponse().beforeSend(responseMetaData -> {
      Optional<SessionStorage> storageOptional = context.getRequest().maybeGet(SessionStorage.class);
      if (storageOptional.isPresent()) {
        SessionStorage storage = storageOptional.get();
        boolean hasChanged = !context.getRequest().get(InitialStorageContainer.class).isSameAsInitial(storage);
        if (hasChanged) {
          Set<Map.Entry<String, Object>> entries = storage.entrySet();

          if (entries.isEmpty()) {
            invalidateSession(responseMetaData);
          } else {
              ByteBufAllocator bufferAllocator = context.get(ByteBufAllocator.class);
            String cookieValue = sessionService.serializeSession(bufferAllocator, entries);
            responseMetaData.cookie(sessionName, cookieValue);
          }
        }
      }
    });

    context.insert(handler);
  }

  private void invalidateSession(ResponseMetaData responseMetaData) {
    responseMetaData.expireCookie(sessionName);
  }

}