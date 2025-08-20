
package dmo.fs.db.wsnext.neo4j;

import dmo.fs.db.MessageUser;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Promise;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.neo4j.driver.reactive.ReactiveResult;
import org.neo4j.driver.reactive.ReactiveSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

public abstract class DbNeo4jBase {
    protected static final Logger logger = LoggerFactory.getLogger(DbNeo4jBase.class.getName());
    protected Driver driver;
    boolean debug = false;

    public abstract MessageUser createMessageUser();

    public Uni<MessageUser> addUser(MessageUser messageUser) {
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("name", messageUser.getName());
        params.put("password", messageUser.getPassword());
        if (messageUser.getIp() == null) {
            params.put("ip", "unknown");
        } else {
            params.put("ip", messageUser.getIp());
        }
        params.put("zone", TimeZone.getDefault().getID());

        return Uni.createFrom().emitter(e -> Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
            session -> session.executeWrite(tx -> {
                Flow.Publisher<ReactiveResult> reactiveResult =
                  tx.run("create (u:User {name: $name, password: $password, ip: $ip, lastLogin: datetime({timezone:$zone})}) return u;", params);
                return Multi.createFrom().publisher(reactiveResult).flatMap(ReactiveResult::records);
            }))
          .withFinalizer(session -> {
              Uni.createFrom().publisher(session.close());
          })
          .onFailure().invoke(Throwable::printStackTrace)
          .map(record -> {
              ZonedDateTime zonedDateTime = record.get("u").get("lastLogin").asZonedDateTime();
              messageUser.setLastLogin(zonedDateTime);
              return messageUser;
          })
          .subscribe().with(e::complete)).map(record -> messageUser);
    }

    public Uni<MessageUser> updateUser(MessageUser messageUser) {
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("name", messageUser.getName());
        params.put("password", messageUser.getPassword());
        params.put("zone", TimeZone.getDefault().getID());

        return Uni.createFrom().emitter(e -> Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
            session -> session.executeWrite(tx -> {
                var results = tx.run("MATCH (u:User {name: $name, password: $password}) SET u.lastLogin = dateTime({timezone:$zone}) return u;", params);
                return Multi.createFrom().publisher(results).flatMap(ReactiveResult::records);
            }))
          .withFinalizer(session -> {
              session.close();
              return Uni.createFrom().nothing();
          })
          .onFailure().invoke(Throwable::printStackTrace)
          .map(record -> {
              List<Value> nodes = record.values();
              ZonedDateTime zonedDateTime = nodes.get(0).get("lastLogin").asZonedDateTime();
              messageUser.setLastLogin(zonedDateTime);
              return record;
          })
          .subscribe().with(e::complete)).map(record -> messageUser);
    }

    public Promise<Map<String, Integer>> processUserMessages(WebSocketConnection connection, MessageUser messageUser)
      throws Exception {
        Promise<Map<String, Integer>> promise = Promise.promise();
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        Map<String, Object> params = new HashMap<>();
        params.put("user", messageUser.getName());
        messageUser.setLastLogin(new Timestamp(System.currentTimeMillis()));
        counts.put("messages", 0);
        WebSocketConnection ws = getThisWebSocket(connection);

        Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
            session -> session.executeRead(tx -> {
                var results = tx.run("match (m:Message), (u:User) where m.user = $user and u.name = $user RETURN m, u;", params);
                return Multi.createFrom().publisher(results).flatMap(ReactiveResult::records);
            }))
          .withFinalizer(session -> {
              session.close();
              removeMessages(messageUser.getName());
              promise.complete(counts);

              return Uni.createFrom().nothing();
          })
          .onFailure().invoke(Throwable::printStackTrace)
          .onItem().invoke(record -> {
              List<Value> nodes = record.values();

              String fromUser = nodes.get(0).get("fromHandle").asString();
              String message = nodes.get(0).get("message").asString();
              ZonedDateTime postDate = nodes.get(0).get("postDate").asZonedDateTime();
              DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd-HH:mm:ss z");

              ws.sendText(fromUser + postDate.format(formatter) + " " + message).subscribe().asCompletionStage();
              Integer count = counts.get("messages") + 1;
              counts.put("messages", count);
          })
          .subscribe().asStream();

        return promise;
    }

    public void removeMessages(String user) {
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("user", user);

        Multi.createFrom().resource(driver::session,
            session -> session.executeWrite(tx -> {
                Result results = tx.run("MATCH (m:Message {user: $user}) DETACH DELETE m;", params);

                return Multi.createFrom().item(results).onItem().call(record -> Uni.createFrom().nothing())
                  .map(record -> "removed");
            }))
          .withFinalizer(session -> {
              session.close();
              return Uni.createFrom().nothing();
          })
          .onFailure().invoke(Throwable::printStackTrace)
          .subscribe().asStream();
    }

    public Promise<MessageUser> deleteUser(WebSocketConnection ws, MessageUser messageUser)
      throws InterruptedException, ExecutionException {
        Promise<MessageUser> promise = Promise.promise();
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("name", messageUser.getName());

        Multi.createFrom().resource(driver::session,
            session -> session.executeWrite(tx -> {
                Result results = tx.run("MATCH (u:User) where u.name = $name delete u;", params);
                return Multi.createFrom().item(results).map(record -> "delete");
            }))
          .withFinalizer(session -> {
              promise.complete(messageUser);
              session.close();
              return Uni.createFrom().nothing();
          })
          .onFailure().invoke(Throwable::printStackTrace)
          .subscribe().asStream();

        return promise;
    }

    public Promise<MessageUser> addMessage(MessageUser messageUser, String message,
                                           List<String> undelivered) throws InterruptedException, ExecutionException {
        Promise<MessageUser> promise = Promise.promise();
        Map<String, Object> params = new HashMap<>();
        params.put("message", message);
        params.put("fromHandle", messageUser.getName());
        params.put("zone", TimeZone.getDefault().getID());
        messageUser.setLastLogin(new Timestamp(System.currentTimeMillis()));

        Uni.createFrom().item(driver.session(ReactiveSession.class)).call(session -> {
            Multi.createFrom().emitter(multiEmitter -> {
                  for (String user : undelivered) {
                      multiEmitter.emit(user);
                  }
                  multiEmitter.complete();
                  var relationship = session.run("MATCH (u:User) MATCH (m:Message) WHERE m.user IN u.name MERGE (u)-[:Undelivered]->(m) return m.user;");
                  Multi.createFrom().publisher(relationship).flatMap(ReactiveResult::records).subscribe().asStream();
              }).onItem().transform(Object::toString).invoke(user -> {
                  params.put("user", user);
                  var results = session.run("create (m:Message {user: $user, message: $message, fromHandle: $fromHandle, postDate: datetime({timezone:$zone})});", params);
                  Multi.createFrom().publisher(results).flatMap(ReactiveResult::records).subscribe().asStream();
              }).onTermination()
              .invoke(() -> {
                  // For debugging
                  if (debug) {
                      var results = session.run("MATCH (u:User) MATCH (m:Message) WHERE m.user IN u.name MERGE (u)-[:Undelivered]->(m) return m.user;");
                      // non-blocking
                      Multi.createFrom().publisher(results).flatMap(ReactiveResult::records)
                        .collect().asMultiMap(r -> {
                            logger.warn("To user of Undelivered message: {}", r.values());
                            return r;
                        }).subscribeAsCompletionStage().whenComplete((r, t) -> logger.warn("Finished"));
                  }
                  session.close();
                  promise.complete(messageUser);
              })
              .subscribe().asStream();
            return Uni.createFrom().nothing();
        }).subscribe().asCompletionStage();

        return promise;
    }

    public Promise<MessageUser> selectUser(MessageUser messageUser)
      throws InterruptedException, SQLException, ExecutionException {
        Promise<MessageUser> promise = Promise.promise();
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("name", messageUser.getName());
        params.put("password", messageUser.getPassword());

        Uni.createFrom().item(driver::session).call(session -> {
            Result result = session.run("MATCH (u:User) where u.name = $name and u.password = $password RETURN count(*) as count;", params);
            Uni.createFrom().item(result).call(record -> {
                int count = record.peek().get("count").asInt();
                session.close();
                if (count == 0) {
                    addUser(messageUser)
                      .subscribeAsCompletionStage().thenAccept(promise::complete);
                } else {
                    updateUser(messageUser)
                      .onItem().invoke(updatedUser -> promise.complete(updatedUser))
                      .subscribeAsCompletionStage().isDone();
                }
                return Uni.createFrom().nothing();
            }).onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();
            return Uni.createFrom().item(session);
        }).onFailure().invoke(Throwable::printStackTrace).subscribeAsCompletionStage().isDone();
        return promise;
    }

    public Promise<StringBuilder> buildUsersJson(MessageUser messageUser)
      throws InterruptedException {
        Promise<StringBuilder> promise = Promise.promise();
        JsonArray ja = new JsonArray();
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("name", messageUser.getName());

        Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
            session -> session.executeRead(tx -> {
                var results = tx.run("MATCH (u:User) where u.name <> $name RETURN u.name as name;", params);
                return Multi.createFrom().publisher(results).flatMap(ReactiveResult::records);
            }))
          .withFinalizer(session -> {
              session.close();
              promise.complete(new StringBuilder(ja.toString()));
              return Uni.createFrom().nothing();
          })
          .onFailure().invoke(Throwable::printStackTrace)
          .map(record -> record.get("name").asString())
          .onItem().invoke(record -> ja.add(new JsonObject().put("name", record)))
          .subscribe().asStream();

        return promise;
    }

    protected WebSocketConnection getThisWebSocket(WebSocketConnection connection) {
        return connection.getOpenConnections().stream()
          .filter(s -> s.id().equals(connection.id())).findFirst().orElse(connection);
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(Driver driver) {
        this.driver = driver;
    }
}
