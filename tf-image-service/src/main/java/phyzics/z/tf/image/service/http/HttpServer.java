/*
 * Copyright 2019 phyzics.z <phyzics.z@gmail.com>.
 *
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
 */
package phyzics.z.tf.image.service.http;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.marshalling.Marshaller;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author phyzics.z <phyzics.z@gmail.com>
 */
public class HttpServer extends AllDirectives {
     private static Logger logger = LoggerFactory.getLogger( HttpServer.class );
    private String address = "localhost";
    private Integer port = 8080;
    private CompletionStage<ServerBinding> binding;
    final ActorSystem system;
    final Materializer materializer;

    public HttpServer(final ActorSystem system, final Materializer materializer) {
        this.system = system;
        this.materializer = materializer;
    }

    public HttpServer address(final String address) {
        this.address = address;
        return this;
    }

    public HttpServer port(final Integer port) {
        this.port = port;
        return this;
    }

    public HttpServer start() {
        final Http http = Http.get(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = createRoute().flow(system, materializer);
        binding = http.bindAndHandle(routeFlow,
                ConnectHttp.toHost(address, port), materializer);

        return this;
    }

    public HttpServer stop() {
        binding
                .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
        return this;
    }

    private Route createRoute() {
        final Unmarshaller<HttpEntity, Image> unmarshaller = Jackson.unmarshaller(Image.class);
        final Marshaller<Image, HttpResponse> marshaller = Marshaller.entityToOKResponse(Jackson.<Image>marshaller());

        final Function<Image, Image> updateImage = image -> {
            logger.info("processing file...");
            //... some processing logic...
            //return the person
            return image;
        };

       final Route route = handleWith(unmarshaller, marshaller, updateImage);


        return route;
    }

}
