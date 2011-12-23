/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.fedora;

import org.gradle.api.internal.ClassPathProvider;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Override most bits of DefaultClassPathProvider.
 *
 * TODO: this thing is just an ugly hack
 * There should really be a (JAR) Service plug point backing this.
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class FedoraClassPathProvider implements ClassPathProvider {
    private <T> Set<T> asSet(T... a) {
        if (a == null)
            return Collections.emptySet();
        final Set<T> set = new HashSet<T>();
        for (T e : a) {
            set.add(e);
        }
        return set;
    }

    public Set<File> findClassPath(String name) {
//        System.err.println("NYI: org.gradle.api.internal.fedora.FedoraClassPathProvider.findClassPath(" + name + ")");
//        // DefaultIsolatedAntBuilder
//        if (name.equals("ANT"))
//            return unmodifiableSet(asSet(new File("/usr/share/java/ant.jar"), new File("/usr/share/java/ant-launcher.jar")));
//        // AntGroovyCompiler
//        else if (name.equals("COMMONS_CLI"))
//            return unmodifiableSet(asSet(new File("/usr/share/java/commons-cli.jar")));
//        else if (name.equals("GROOVY") || name.equals("LOCAL_GROOVY"))
//            return unmodifiableSet(asSet(new File("/usr/share/java/groovy.jar")));
        // on Fedora groovy is split up, there is no groovy-all
        if (name.equals("GROOVY") || name.equals("LOCAL_GROOVY")) {
            return Collections.unmodifiableSet(asSet(
                    new File("/usr/share/java/groovy.jar"),
                    new File("/usr/share/java/antlr.jar"),
                    new File("/usr/share/java/objectweb-asm/asm-all.jar")));
        }
        return null;
    }
}
