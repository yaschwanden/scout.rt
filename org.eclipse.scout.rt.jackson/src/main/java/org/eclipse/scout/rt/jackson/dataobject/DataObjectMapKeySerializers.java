/*******************************************************************************
 * Copyright (c) 2010-2018 BSI Business Systems Integration AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     BSI Business Systems Integration AG - initial API and implementation
 ******************************************************************************/
package org.eclipse.scout.rt.jackson.dataobject;

import java.util.Locale;

import org.eclipse.scout.rt.platform.Bean;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.Serializers;

/**
 * Set of custom serializers handling map keys.
 * <p>
 * Subclass and extend this class to provide support for additional serializer for Map keys of various types.
 * <p>
 * <b>Note:</b> The difference between a key serializer and an ordinary one is that the former transforms a map key into
 * a JSON name (i.e. always typed string), whereas the latter writes a JSON value (i.e. any JSON type, including objects
 * and arrays).
 * <p>
 * TODO [9.1] pbz: [JSON] Remove this class when Jackson is upgraded to 3.0 (issue 1600) (and LocaleMapKeySerializer is
 * the only key serializer)
 */
@Bean
public class DataObjectMapKeySerializers extends Serializers.Base {

  protected ScoutDataObjectModuleContext m_moduleContext;

  public DataObjectMapKeySerializers withModuleContext(ScoutDataObjectModuleContext moduleContext) {
    m_moduleContext = moduleContext;
    return this;
  }

  @Override
  public JsonSerializer<?> findSerializer(SerializationConfig config, JavaType type, BeanDescription beanDesc) {
    Class<?> rawClass = type.getRawClass();
    if (Locale.class.isAssignableFrom(rawClass)) {
      return new LocaleMapKeySerializer();
    }
    return null;
  }
}
