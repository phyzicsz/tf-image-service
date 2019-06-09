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

package phyzicsz.tf.image.service;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import phyzicsz.tf.image.service.http.HttpServer;

/**
 *
 * @author phyzics.z <phyzics.z@gmail.com>
 */
public class AppFactory {
    private Config config;
    private ActorSystem system;
    private Materializer materializer;

    public AppFactory init(){
        config = ConfigFactory.load();
        system = ActorSystem.create("tf-image-service", config);
        materializer = ActorMaterializer.create(system);
        return this;
    }
    
    public AppFactory startServer(){
        String address = config.getString("http-server.address");
        Integer port = config.getInt("http-server.port");
        
        HttpServer server = new HttpServer(system, materializer);
        server.address(address)
                .port(port)
                .start();
        
        return this;
    }
}
