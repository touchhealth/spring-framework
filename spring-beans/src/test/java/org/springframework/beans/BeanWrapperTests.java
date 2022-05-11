/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import org.springframework.core.OverridingClassLoader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.tests.sample.beans.TestBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * Specific {@link BeanWrapperImpl} tests.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Alef Arendsen
 * @author Arjen Poutsma
 * @author Chris Beams
 * @author Dave Syer
 */
public class BeanWrapperTests extends AbstractPropertyAccessorTests {

	@Override
	protected BeanWrapperImpl createAccessor(Object target) {
		return new BeanWrapperImpl(target);
	}


	@Test
	public void setterDoesNotCallGetter() {
		GetterBean target = new GetterBean();
		BeanWrapper accessor = createAccessor(target);
		accessor.setPropertyValue("name", "tom");
		assertTrue("Set name to tom", target.getName().equals("tom"));
	}

	@Test
	public void getterSilentlyFailWithOldValueExtraction() {
		GetterBean target = new GetterBean();
		BeanWrapper accessor = createAccessor(target);
		accessor.setExtractOldValueForEditor(true); // This will call the getter
		accessor.setPropertyValue("name", "tom");
		assertTrue("Set name to tom", target.getName().equals("tom"));
	}

	@Test
	public void aliasedSetterThroughDefaultMethod() {
		GetterBean target = new GetterBean();
		BeanWrapper accessor = createAccessor(target);
		accessor.setPropertyValue("aliasedName", "tom");
		assertTrue("Set name to tom", target.getAliasedName().equals("tom"));
	}

	@Test
	public void setValidAndInvalidPropertyValuesShouldContainExceptionDetails() {
		TestBean target = new TestBean();
		String newName = "tony";
		String invalidTouchy = ".valid";
		try {
			BeanWrapper accessor = createAccessor(target);
			MutablePropertyValues pvs = new MutablePropertyValues();
			pvs.addPropertyValue(new PropertyValue("age", "foobar"));
			pvs.addPropertyValue(new PropertyValue("name", newName));
			pvs.addPropertyValue(new PropertyValue("touchy", invalidTouchy));
			accessor.setPropertyValues(pvs);
			fail("Should throw exception when everything is valid");
		}
		catch (PropertyBatchUpdateException ex) {
			assertTrue("Must contain 2 exceptions", ex.getExceptionCount() == 2);
			// Test validly set property matches
			assertTrue("Vaid set property must stick", target.getName().equals(newName));
			assertTrue("Invalid set property must retain old value", target.getAge() == 0);
			assertTrue("New value of dodgy setter must be available through exception",
					ex.getPropertyAccessException("touchy").getPropertyChangeEvent().getNewValue().equals(invalidTouchy));
		}
	}

	@Test
	public void checkNotWritablePropertyHoldPossibleMatches() {
		TestBean target = new TestBean();
		try {
			BeanWrapper accessor = createAccessor(target);
			accessor.setPropertyValue("ag", "foobar");
			fail("Should throw exception on invalid property");
		}
		catch (NotWritablePropertyException ex) {
			// expected
			assertEquals(1, ex.getPossibleMatches().length);
			assertEquals("age", ex.getPossibleMatches()[0]);
		}
	}

	@Test // Can't be shared; there is no such thing as a read-only field
	public void setReadOnlyMapProperty() {
		TypedReadOnlyMap map = new TypedReadOnlyMap(Collections.singletonMap("key", new TestBean()));
		TypedReadOnlyMapClient target = new TypedReadOnlyMapClient();
		BeanWrapper accessor = createAccessor(target);
		accessor.setPropertyValue("map", map);
	}

	@Test
	public void notWritablePropertyExceptionContainsAlternativeMatch() {
		IntelliBean target = new IntelliBean();
		BeanWrapper bw = createAccessor(target);
		try {
			bw.setPropertyValue("names", "Alef");
		}
		catch (NotWritablePropertyException ex) {
			assertNotNull("Possible matches not determined", ex.getPossibleMatches());
			assertEquals("Invalid amount of alternatives", 1, ex.getPossibleMatches().length);
		}
	}

	@Test
	public void notWritablePropertyExceptionContainsAlternativeMatches() {
		IntelliBean target = new IntelliBean();
		BeanWrapper bw = createAccessor(target);
		try {
			bw.setPropertyValue("mystring", "Arjen");
		}
		catch (NotWritablePropertyException ex) {
			assertNotNull("Possible matches not determined", ex.getPossibleMatches());
			assertEquals("Invalid amount of alternatives", 3, ex.getPossibleMatches().length);
		}
	}

	@Test  // Can't be shared: no type mismatch with a field
	public void setPropertyTypeMismatch() {
		PropertyTypeMismatch target = new PropertyTypeMismatch();
		BeanWrapper accessor = createAccessor(target);
		accessor.setPropertyValue("object", "a String");
		assertEquals("a String", target.value);
		assertTrue(target.getObject() == 8);
		assertEquals(8, accessor.getPropertyValue("object"));
	}

	@Test
	public void propertyDescriptors() throws Exception {
		TestBean target = new TestBean();
		target.setSpouse(new TestBean());
		BeanWrapper accessor = createAccessor(target);
		accessor.setPropertyValue("name", "a");
		accessor.setPropertyValue("spouse.name", "b");

		assertThat(target.getName()).isEqualTo("a");
		assertThat(target.getSpouse().getName()).isEqualTo("b");
		assertThat(accessor.getPropertyValue("name")).isEqualTo("a");
		assertThat(accessor.getPropertyValue("spouse.name")).isEqualTo("b");
		assertThat(accessor.getPropertyDescriptor("name").getPropertyType()).isEqualTo(String.class);
		assertThat(accessor.getPropertyDescriptor("spouse.name").getPropertyType()).isEqualTo(String.class);

		assertThat(accessor.isReadableProperty("class.package")).isFalse();
		assertThat(accessor.isReadableProperty("class.module")).isFalse();
		assertThat(accessor.isReadableProperty("class.classLoader")).isFalse();
		assertThat(accessor.isReadableProperty("class.name")).isTrue();
		assertThat(accessor.isReadableProperty("class.simpleName")).isTrue();
		assertThat(accessor.getPropertyValue("class.name")).isEqualTo(TestBean.class.getName());
		assertThat(accessor.getPropertyValue("class.simpleName")).isEqualTo(TestBean.class.getSimpleName());
		assertThat(accessor.getPropertyDescriptor("class.name").getPropertyType()).isEqualTo(String.class);
		assertThat(accessor.getPropertyDescriptor("class.simpleName").getPropertyType()).isEqualTo(String.class);

		accessor = createAccessor(new DefaultResourceLoader());

		assertThat(accessor.isReadableProperty("class.package")).isFalse();
		assertThat(accessor.isReadableProperty("class.module")).isFalse();
		assertThat(accessor.isReadableProperty("class.classLoader")).isFalse();
		assertThat(accessor.isReadableProperty("class.name")).isTrue();
		assertThat(accessor.isReadableProperty("class.simpleName")).isTrue();
		assertThat(accessor.isReadableProperty("classLoader")).isTrue();
		assertThat(accessor.isWritableProperty("classLoader")).isTrue();
		OverridingClassLoader ocl = new OverridingClassLoader(getClass().getClassLoader());
		accessor.setPropertyValue("classLoader", ocl);
		assertThat(accessor.getPropertyValue("classLoader")).isSameAs(ocl);

		accessor = createAccessor(new UrlResource("https://spring.io"));

		assertThat(accessor.isReadableProperty("class.package")).isFalse();
		assertThat(accessor.isReadableProperty("class.module")).isFalse();
		assertThat(accessor.isReadableProperty("class.classLoader")).isFalse();
		assertThat(accessor.isReadableProperty("class.name")).isTrue();
		assertThat(accessor.isReadableProperty("class.simpleName")).isTrue();
		assertThat(accessor.isReadableProperty("URL.protocol")).isTrue();
		assertThat(accessor.isReadableProperty("URL.host")).isTrue();
		assertThat(accessor.isReadableProperty("URL.port")).isTrue();
		assertThat(accessor.isReadableProperty("URL.file")).isTrue();
		assertThat(accessor.isReadableProperty("URL.content")).isFalse();
		assertThat(accessor.isReadableProperty("inputStream")).isFalse();
		assertThat(accessor.isReadableProperty("filename")).isTrue();
		assertThat(accessor.isReadableProperty("description")).isTrue();
	}

	@Test
	public void getPropertyWithOptional() {
		GetterWithOptional target = new GetterWithOptional();
		TestBean tb = new TestBean("x");
		BeanWrapper accessor = createAccessor(target);

		accessor.setPropertyValue("object", tb);
		assertSame(tb, target.value);
		assertSame(tb, target.getObject().get());
		assertSame(tb, ((Optional<String>) accessor.getPropertyValue("object")).get());
		assertEquals("x", target.value.getName());
		assertEquals("x", target.getObject().get().getName());
		assertEquals("x", accessor.getPropertyValue("object.name"));

		accessor.setPropertyValue("object.name", "y");
		assertSame(tb, target.value);
		assertSame(tb, target.getObject().get());
		assertSame(tb, ((Optional<String>) accessor.getPropertyValue("object")).get());
		assertEquals("y", target.value.getName());
		assertEquals("y", target.getObject().get().getName());
		assertEquals("y", accessor.getPropertyValue("object.name"));
	}

	@Test
	public void getPropertyWithOptionalAndAutoGrow() {
		GetterWithOptional target = new GetterWithOptional();
		BeanWrapper accessor = createAccessor(target);
		accessor.setAutoGrowNestedPaths(true);

		accessor.setPropertyValue("object.name", "x");
		assertEquals("x", target.value.getName());
		assertEquals("x", target.getObject().get().getName());
		assertEquals("x", accessor.getPropertyValue("object.name"));
	}

	@Test
	public void incompletelyQuotedKeyLeadsToPropertyException() {
		TestBean target = new TestBean();
		try {
			BeanWrapper accessor = createAccessor(target);
			accessor.setPropertyValue("[']", "foobar");
			fail("Should throw exception on invalid property");
		}
		catch (NotWritablePropertyException ex) {
			assertNull(ex.getPossibleMatches());
		}
	}


	@SuppressWarnings("unused")
	private interface AliasedProperty {

		default void setAliasedName(String name) {
			setName(name);
		}

		default String getAliasedName() {
			return getName();
		}

		void setName(String name);

		String getName();
	}


	@SuppressWarnings("unused")
	private static class GetterBean implements AliasedProperty {

		private String name;

		public void setName(String name) {
			this.name = name;
		}

		public String getName() {
			if (this.name == null) {
				throw new RuntimeException("name property must be set");
			}
			return name;
		}
	}


	@SuppressWarnings("unused")
	private static class IntelliBean {

		public void setName(String name) {
		}

		public void setMyString(String string) {
		}

		public void setMyStrings(String string) {
		}

		public void setMyStriNg(String string) {
		}

		public void setMyStringss(String string) {
		}
	}


	@SuppressWarnings("serial")
	public static class TypedReadOnlyMap extends ReadOnlyMap<String, TestBean> {

		public TypedReadOnlyMap() {
		}

		public TypedReadOnlyMap(Map<? extends String, ? extends TestBean> map) {
			super(map);
		}
	}


	public static class TypedReadOnlyMapClient {

		public void setMap(TypedReadOnlyMap map) {
		}
	}


	public static class PropertyTypeMismatch {

		public String value;

		public void setObject(String object) {
			this.value = object;
		}

		public Integer getObject() {
			return (this.value != null ? this.value.length() : null);
		}
	}


	public static class GetterWithOptional {

		public TestBean value;

		public void setObject(TestBean object) {
			this.value = object;
		}

		public Optional<TestBean> getObject() {
			return Optional.ofNullable(this.value);
		}
	}

}
