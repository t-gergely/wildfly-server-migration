/*
 * Copyright 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.migration.core;

import java.nio.file.Path;

/**
 * A migration source and/or target server.
 * @author emmartins
 */
public interface Server {

    /**
     * Retrieves the server's base directory.
     * @return the server's base directory
     */
    Path getBaseDir();

    /**
     * Retrieves the server's product info.
     * @return the server's product info
     */
    ProductInfo getProductInfo();

    /**
     * Retrieves the task to migrate from the specified source server.
     * @param source the server to migrate from
     * @return the task to migrate from the specified source server
     * @throws IllegalArgumentException if the server is not able to migrate from the specified source
     */
    ServerMigrationTask getServerMigrationTask(Server source) throws IllegalArgumentException;
}
