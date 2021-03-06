/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.geogig.storage.bdbje;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.StorageType;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.locationtech.geogig.storage.datastream.LZFSerializationFactory;

import com.google.inject.Inject;

public final class JEObjectDatabase_v0_1 extends JEObjectDatabase {
    @Inject
    public JEObjectDatabase_v0_1(final ConfigDatabase configDB,
            final EnvironmentBuilder envProvider, final Hints hints) {
        this(configDB, envProvider, hints.getBoolean(Hints.OBJECTS_READ_ONLY),
                JEObjectDatabase.ENVIRONMENT_NAME);
    }

    public JEObjectDatabase_v0_1(final ConfigDatabase configDB,
            final EnvironmentBuilder envProvider, final boolean readOnly, final String envName) {
        super(new LZFSerializationFactory(DataStreamSerializationFactoryV1.INSTANCE), configDB,
                envProvider, readOnly, envName);
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        StorageType.OBJECT.configure(configDB, "bdbje", "0.1");
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        StorageType.OBJECT.verify(configDB, "bdbje", "0.1");
    }
}
