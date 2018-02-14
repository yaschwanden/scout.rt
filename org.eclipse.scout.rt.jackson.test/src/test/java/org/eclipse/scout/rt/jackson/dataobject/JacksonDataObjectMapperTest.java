/*
 * Copyright (c) BSI Business Systems Integration AG. All rights reserved.
 * http://www.bsiag.com/
 */
package org.eclipse.scout.rt.jackson.dataobject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.eclipse.scout.rt.jackson.dataobject.fixture.ITestBaseEntityDo;
import org.eclipse.scout.rt.jackson.dataobject.fixture.TestComplexEntityDo;
import org.eclipse.scout.rt.jackson.dataobject.fixture.TestCustomImplementedEntityDo;
import org.eclipse.scout.rt.jackson.dataobject.fixture.TestEntityWithInterface1Do;
import org.eclipse.scout.rt.jackson.testing.DataObjectSerializationTestHelper;
import org.eclipse.scout.rt.platform.BEANS;
import org.eclipse.scout.rt.platform.BeanMetaData;
import org.eclipse.scout.rt.platform.IBean;
import org.eclipse.scout.rt.platform.dataobject.DataObjectHelper;
import org.eclipse.scout.rt.platform.dataobject.DoEntity;
import org.eclipse.scout.rt.platform.dataobject.DoEntityHolder;
import org.eclipse.scout.rt.platform.dataobject.IDataObjectMapper;
import org.eclipse.scout.rt.platform.exception.PlatformException;
import org.eclipse.scout.rt.platform.util.CloneUtility;
import org.eclipse.scout.rt.testing.shared.TestingUtility;
import org.junit.Before;
import org.junit.Test;

/**
 * Various test cases with requires a real jackson serializer/deserializer for testing
 */
public class JacksonDataObjectMapperTest {

  protected DataObjectSerializationTestHelper m_testHelper;
  protected JacksonDataObjectMapper m_mapper;

  @Before
  public void before() {
    m_testHelper = BEANS.get(DataObjectSerializationTestHelper.class);
    m_mapper = BEANS.get(JacksonDataObjectMapper.class);
  }

  @Test
  public void testReadWriteValue() {
    assertNull(m_mapper.writeValue(null));
    assertNull(m_mapper.readValue(null, null));
    assertNull(m_mapper.readValue(null, Object.class));

    DoEntity entity = new DoEntity();
    entity.put("foo", "bar");
    entity.put("baz", 42);
    String json = m_mapper.writeValue(entity);
    DoEntity parsedEntity = m_mapper.readValue(json, DoEntity.class);
    String jsonParsedEntity = m_mapper.writeValue(parsedEntity);
    m_testHelper.assertJsonEquals(json, jsonParsedEntity);
  }

  @Test(expected = PlatformException.class)
  public void testWriteValueException() {
    m_mapper.writeValue(new Object());
  }

  @Test(expected = PlatformException.class)
  public void testReadValueException() {
    m_mapper.readValue("{\"foo\" : 1}", BigDecimal.class);
  }

  @Test
  public void testToString() {
    // register DataObjectMapper using default (non-testing) JacksonDataObjectMapper implementation of IDataObjectMapper
    List<IBean<?>> registeredBeans = TestingUtility.registerBeans(
        new BeanMetaData(DataObjectHelper.class).withOrder(1).withApplicationScoped(true),
        new BeanMetaData(IDataObjectMapper.class).withOrder(1).withApplicationScoped(true).withInitialInstance(new JacksonDataObjectMapper()));
    try {
      DoEntity entity = BEANS.get(DoEntity.class);
      entity.put("stringAttribute", "foo");
      entity.put("intAttribute", 42);
      entity.putList("listAttribute", Arrays.asList(1, 2, 3));
      assertEquals("DoEntity {\"intAttribute\":42,\"listAttribute\":[1,2,3],\"stringAttribute\":\"foo\"}", entity.toString());
    }
    finally {
      TestingUtility.unregisterBeans(registeredBeans);
    }
  }

  @Test
  public void testCloneDoEntity() throws Exception {
    DoEntityHolder<DoEntity> holder = new DoEntityHolder<>();
    DoEntity entity = new DoEntity();
    entity.put("foo", "bar");
    entity.put("42", 1234.56);
    holder.setValue(entity);

    DoEntityHolder<DoEntity> holderClone = CloneUtility.createDeepCopyBySerializing(holder);
    m_testHelper.assertDoEntityEquals(entity, holderClone.getValue());
  }

  @Test
  public void testCloneComplexDoEntity() throws Exception {
    TestComplexEntityDo testDo = BEANS.get(TestComplexEntityDo.class);
    testDo.id().set("4d2abc01-afc0-49f2-9eee-a99878d49728");
    testDo.stringAttribute().set("foo");
    testDo.integerAttribute().set(42);
    testDo.longAttribute().set(123l);
    testDo.floatAttribute().set(12.34f);
    testDo.doubleAttribute().set(56.78);
    testDo.bigDecimalAttribute().set(new BigDecimal("1.23456789"));
    testDo.bigIntegerAttribute().set(new BigInteger("123456789"));
    testDo.dateAttribute().set(new Date(123456789));
    testDo.objectAttribute().set(new String("fooObject"));
    testDo.withUuidAttribute(UUID.fromString("298d64f9-821d-49fe-91fb-6fb9860d4950"));
    testDo.withLocaleAttribute(Locale.forLanguageTag("de-CH"));

    DoEntityHolder<TestComplexEntityDo> holder = new DoEntityHolder<>();
    holder.setValue(testDo);

    DoEntityHolder<TestComplexEntityDo> holderClone = CloneUtility.createDeepCopyBySerializing(holder);
    m_testHelper.assertDoEntityEquals(testDo, holderClone.getValue());
  }

  @Test
  public void testCloneDoEntityWithInterface() throws Exception {
    DoEntityHolder<ITestBaseEntityDo> holder = new DoEntityHolder<>();
    holder.setValue(BEANS.get(TestEntityWithInterface1Do.class));
    holder.getValue().stringAttribute().set("foo");

    DoEntityHolder<ITestBaseEntityDo> holderClone = CloneUtility.createDeepCopyBySerializing(holder);
    m_testHelper.assertDoEntityEquals(holder.getValue(), holderClone.getValue());
  }

  @Test
  public void testCloneCustomImplementedEntityDo() throws Exception {
    DoEntityHolder<TestCustomImplementedEntityDo> holder = new DoEntityHolder<>();
    holder.setValue(BEANS.get(TestCustomImplementedEntityDo.class));
    holder.getValue().put("stringAttribute", "foo");

    DoEntityHolder<TestCustomImplementedEntityDo> holderClone = CloneUtility.createDeepCopyBySerializing(holder);
    m_testHelper.assertDoEntityEquals(holder.getValue(), holderClone.getValue());
  }

}
