/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.vertx;

import io.atomix.catalyst.util.Assert;
import io.atomix.collections.DistributedMultiMap;
import io.atomix.collections.DistributedSet;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.AsyncMultiMap;
import io.vertx.core.spi.cluster.ChoosableIterable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Atomix async multi map.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class AtomixAsyncMultiMap<K, V> implements AsyncMultiMap<K, V> {
  private final Vertx vertx;
  private final DistributedMultiMap<K, V> map;
  private final DistributedSet<K> keys;

  public AtomixAsyncMultiMap(Vertx vertx, DistributedMultiMap<K, V> map, DistributedSet<K> keys) {
    this.vertx = Assert.notNull(vertx, "vertx");
    this.map = Assert.notNull(map, "map");
    this.keys = Assert.notNull(keys, "keys");
  }

  @Override
  public void add(K k, V v, Handler<AsyncResult<Void>> handler) {
      keys.add(k)
              .thenCompose( x -> map.put(k, v))
              .whenComplete(VertxFutures.voidHandler(handler, vertx.getOrCreateContext()) );

  }

  @Override
  public void get(K k, Handler<AsyncResult<ChoosableIterable<V>>> handler) {
    map.get(k).whenComplete(VertxFutures.convertHandler(handler, AtomixChoosableIterable::new, vertx.getOrCreateContext()));
  }

  @Override
  public void remove(K k, V v, Handler<AsyncResult<Boolean>> handler) {
    map.remove(k, v).whenComplete(VertxFutures.resultHandler(handler, vertx.getOrCreateContext()));
  }

  @Override
  public void removeAllForValue(V v, Handler<AsyncResult<Void>> handler) {
    map.removeValue(v).whenComplete(VertxFutures.voidHandler(handler, vertx.getOrCreateContext()));
  }

    /**
     * Realy stupid implementation. Based on additional set containing keys.
     * May not work.
     * @param p
     * @param handler
     */
  @Override
  public void removeAllMatching(Predicate<V> p, Handler<AsyncResult<Void>> handler) {
      final Set<V> toRemove = Collections.synchronizedSet( new HashSet<>());

      keys.iterator().thenCompose(ki -> {
          final List<CompletableFuture<?>> r2 = new ArrayList<>();
          ki.forEachRemaining((k) -> {
              r2.add(map.get(k).thenCompose( vs -> {
                  toRemove.addAll(vs);
                  return CompletableFuture.completedFuture(true);
              }));

          });
          return CompletableFuture.allOf(r2.toArray(new CompletableFuture<?>[]{}));
      } ).thenCompose(y ->{
          final List<CompletableFuture<?>> r2 = new ArrayList<>();
          toRemove.forEach(x ->{
              r2.add(map.removeValue(x));
          });
          return CompletableFuture.allOf(r2.toArray(new CompletableFuture<?>[]{}));
      }).whenComplete(VertxFutures.voidHandler(handler, vertx.getOrCreateContext()));


    //TODO:proper implementation
//    throw new RuntimeException("NOT IMPLEMENTED");

  }

}
