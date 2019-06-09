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

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author phyzics.z <phyzics.z@gmail.com>
 */
public class HttpServerTest {

    public HttpServerTest() {
    }

    /**
     * Test of address method, of class HttpServer.
     */
    @org.junit.jupiter.api.Test
    public void testServer() throws JsonProcessingException, InterruptedException, ExecutionException, TimeoutException {
        Config config = ConfigFactory.load();
        ActorSystem system = ActorSystem.create("tf-image-service", config);
        Materializer materializer = ActorMaterializer.create(system);

        String address = config.getString("http-server.address");
        Integer port = config.getInt("http-server.port");

        HttpServer server = new HttpServer(system, materializer);
        server.address(address)
                .port(port)
                .start();

        Image image = new Image();
        image.setName("testImage.jpg");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(image);

        HttpRequest response = HttpRequest.POST("http://localhost:8080")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, json));
        
        final CompletionStage<HttpResponse> responseCompletion
                = Http.get(system)
                        .singleRequest(HttpRequest.POST("http://localhost:8080")
                                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, json)));
        
        HttpResponse responseFuture = responseCompletion.toCompletableFuture().get(5, TimeUnit.SECONDS); 
        CompletionStage<Image> imageCompletion = Jackson.unmarshaller(Image.class).unmarshal(responseFuture.entity(), materializer);
        CompletableFuture<Image> imageFuture = imageCompletion.toCompletableFuture();
        Image image2 = imageFuture.get(5, TimeUnit.SECONDS);
        
 //       CompletionStage<Image> completion = Jackson.unmarshaller(Image.class).unmarshal(response.entity(), materializer);
 //       CompletableFuture<Image> imageFuture = completion.toCompletableFuture();
        
        assertEquals(image.getName(), image2.getName());
    }

}
