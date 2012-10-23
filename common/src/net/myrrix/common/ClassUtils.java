/*
 * Copyright Myrrix Ltd
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

package net.myrrix.common;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * {@link Class}-related utility methods.
 */
public final class ClassUtils {

  private static final Class<?>[] NO_TYPES = new Class<?>[0];
  private static final Object[] NO_ARGS = new Object[0];

  private ClassUtils() {
  }

  public static <T> T loadInstanceOf(Class<T> clazz) {
    return loadInstanceOf(clazz.getName(), clazz);
  }

  public static <T> T loadInstanceOf(Class<T> clazz, Class<?>[] constructorTypes, Object[] constructorArgs) {
    return loadInstanceOf(clazz.getName(), clazz, constructorTypes, constructorArgs);
  }

  /**
   * Like {@link #loadInstanceOf(String, Class, Class[], Object[])} for no-arg constructors.
   */
  public static <T> T loadInstanceOf(String implClassName, Class<T> superClass) {
    return loadInstanceOf(implClassName, superClass, NO_TYPES, NO_ARGS);
  }

  /**
   * Loads and instantiates a named implementation class, a subclass of a given supertype,
   * whose constructor takes the given arguments.
   *
   * @param implClassName implementation class name
   * @param superClass superclass or interface that the implementation extends
   * @param constructorTypes argument types of constructor to use
   * @param constructorArgs actual constructor arguments
   * @return instance of {@code implClassName}
   */
  public static <T> T loadInstanceOf(String implClassName,
                                     Class<T> superClass,
                                     Class<?>[] constructorTypes,
                                     Object[] constructorArgs) {
    return doLoadInstanceOf(implClassName,
                            superClass,
                            constructorTypes,
                            constructorArgs,
                            ClassUtils.class.getClassLoader());
  }

  public static <T> T loadFromRemote(String implClassName, Class<T> superClass, URL url) {
    URLClassLoader urlClassloader = new URLClassLoader(new URL[] {url}, ClassUtils.class.getClassLoader());
    return doLoadInstanceOf(implClassName, superClass, NO_TYPES, NO_ARGS, urlClassloader);
    // In Java 7, should call close() on URLClassLoader
  }

  private static <T> T doLoadInstanceOf(String implClassName,
                                        Class<T> superClass,
                                        Class<?>[] constructorTypes,
                                        Object[] constructorArgs,
                                        ClassLoader classLoader) {
    try {
      Class<? extends T> configClass = Class.forName(implClassName, true, classLoader).asSubclass(superClass);
      Constructor<? extends T> constructor = configClass.getConstructor(constructorTypes);
      return constructor.newInstance(constructorArgs);
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalStateException("No valid " + superClass + " binding exists", cnfe);
    } catch (NoSuchMethodException nsme) {
      throw new IllegalStateException("No valid " + superClass + " binding exists", nsme);
    } catch (InvocationTargetException ite) {
      throw new IllegalStateException("Could not instantiate " + superClass + " due to exception", ite.getCause());
    } catch (InstantiationException ie) {
      throw new IllegalStateException("No valid " + superClass + " binding exists", ie);
    } catch (IllegalAccessException iae) {
      throw new IllegalStateException("No valid " + superClass + " binding exists", iae);
    }
  }

  public static boolean classExists(String implClassName) {
    try {
      Class.forName(implClassName);
      return true;
    } catch (ClassNotFoundException cnfe) {
      return false;
    }
  }

}
