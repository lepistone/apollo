/*
 * -\-\-
 * Spotify Apollo Entity Middleware
 * --
 * Copyright (C) 2013 - 2016 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */
package com.spotify.apollo.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.apollo.AppInit;
import com.spotify.apollo.Response;
import com.spotify.apollo.Status;
import com.spotify.apollo.route.Middleware;
import com.spotify.apollo.route.Route;
import com.spotify.apollo.test.ServiceHelper;

import org.junit.Test;

import java.io.IOException;

import io.norberg.automatter.AutoMatter;
import io.norberg.automatter.jackson.AutoMatterModule;
import okio.ByteString;

import static com.fasterxml.jackson.databind.PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES;
import static com.spotify.apollo.entity.EntityMiddlewareTest.await;
import static com.spotify.apollo.entity.JsonMatchers.asStr;
import static com.spotify.apollo.entity.JsonMatchers.hasJsonPath;
import static com.spotify.apollo.test.unit.ResponseMatchers.hasHeader;
import static com.spotify.apollo.test.unit.ResponseMatchers.hasPayload;
import static com.spotify.apollo.test.unit.ResponseMatchers.hasStatus;
import static com.spotify.apollo.test.unit.StatusTypeMatchers.withCode;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class EntityCodecsTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .setPropertyNamingStrategy(CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
      .registerModule(new AutoMatterModule());

  private static final ByteString JSON = ByteString.encodeUtf8(
      "{\"naming_convention_used\":\"for this value\"}");

  private static final ByteString ENTITY = ByteString.of(
      new byte[] {0x45, 0X6e, 0X74, 0X69, 0X74, 0X79});

  @Test
  public void testWithCustomJacksonMapper() throws Exception {
    EntityMiddleware e = EntityMiddleware.forCodec(JacksonEntityCodec.forMapper(OBJECT_MAPPER));

    ServiceHelper service = ServiceHelper.create(entityApp(e), "entity-test");
    service.start();

    Response<ByteString> resp = await(service.request("GET", "/", JSON));
    assertThat(resp, hasStatus(withCode(Status.OK)));
    assertThat(resp, hasHeader("Content-Type", equalTo("application/json")));
    assertThat(resp, hasPayload(asStr(hasJsonPath("naming_convention_used", equalTo("override")))));

    service.close();
  }

  @Test
  public void testWithCustomContentType() throws Exception {
    EntityMiddleware e = EntityMiddleware.forCodec(
        JacksonEntityCodec.forMapper(OBJECT_MAPPER),
        "application/vnd+spotify.test+json");

    ServiceHelper service = ServiceHelper.create(entityApp(e), "entity-test");
    service.start();

    Response<ByteString> resp = await(service.request("GET", "/", JSON));
    assertThat(resp, hasStatus(withCode(Status.OK)));
    assertThat(resp, hasHeader("Content-Type", equalTo("application/vnd+spotify.test+json")));
    assertThat(resp, hasPayload(asStr(hasJsonPath("naming_convention_used", equalTo("override")))));

    service.close();
  }

  @Test
  public void testWithCustomCodec() throws Exception {
    EntityMiddleware e = EntityMiddleware.forCodec(new StringCodec());

    ServiceHelper service = ServiceHelper.create(stringApp(e), "entity-test");
    service.start();

    Response<ByteString> resp = await(service.request("GET", "/", ENTITY));
    assertThat(resp, hasStatus(withCode(Status.OK)));
    assertThat(resp, hasHeader("Content-Type", equalTo("text/plain")));
    assertThat(resp, hasPayload(asStr(equalTo("EntityMiddleware"))));

    service.close();
  }

  @Test
  public void testWithCustomCodecContentType() throws Exception {
    EntityMiddleware e = EntityMiddleware.forCodec(
        new StringCodec(), "text/vnd+spotify.test+plain");

    ServiceHelper service =ServiceHelper.create(stringApp(e), "entity-test");
    service.start();

    Response<ByteString> resp = await(service.request("GET", "/", ENTITY));
    assertThat(resp, hasStatus(withCode(Status.OK)));
    assertThat(resp, hasHeader("Content-Type", equalTo("text/vnd+spotify.test+plain")));
    assertThat(resp, hasPayload(asStr(equalTo("EntityMiddleware"))));

    service.close();
  }

  AppInit entityApp(EntityMiddleware e) {
    return env -> env.routingEngine()
        .registerRoute(
            Route.with(e.direct(Entity.class), "GET", "/", rc -> this::endpoint)
                .withMiddleware(Middleware::syncToAsync));
  }

  Entity endpoint(Entity entity) {
    assertThat(entity.namingConventionUsed(), equalTo("for this value"));
    return EntityBuilder.from(entity)
        .namingConventionUsed("override")
        .build();
  }

  AppInit stringApp(EntityMiddleware e) {
    return env -> env.routingEngine()
        .registerRoute(
            Route.with(e.direct(String.class), "GET", "/", rc ->  this::stringEndpoint)
                .withMiddleware(Middleware::syncToAsync));
  }

  String stringEndpoint(String entity) {
    return entity + "Middleware";
  }

  @AutoMatter
  interface Entity {
    String namingConventionUsed();
  }

  private static final class StringCodec implements EntityCodec {

    @Override
    public String defaultContentType() {
      return "text/plain";
    }

    @Override
    public <E> ByteString write(E entity, Class<? extends E> clazz) throws IOException {
      if (!String.class.equals(clazz)) {
        throw new UnsupportedOperationException("Can only encode strings");
      }

      return ByteString.encodeUtf8((String) entity);
    }

    @Override
    public <E> E read(ByteString data, Class<? extends E> clazz) throws IOException {
      if (!String.class.equals(clazz)) {
        throw new UnsupportedOperationException("Can only encode strings");
      }

      //noinspection unchecked
      return (E) data.utf8();
    }
  }
}
