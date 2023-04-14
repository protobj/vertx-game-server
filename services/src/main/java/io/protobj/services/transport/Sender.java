package io.protobj.services.transport;

import io.scalecube.net.Address;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

public interface Sender {

  Mono<Connection> connect(Address address);

  Mono<Void> send(Message message);
}