/*******************************************************************************
 * Copyright 2013-2014 alladin-IT GmbH
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
 ******************************************************************************/
package at.alladin.rmbt.shared.hstoreparser;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import at.alladin.rmbt.shared.hstoreparser.annotation.HstoreCast;
import at.alladin.rmbt.shared.hstoreparser.annotation.HstoreCollection;
import at.alladin.rmbt.shared.hstoreparser.annotation.HstoreKey;


/**
 * 
 * @author lb
 *
 */
public class HstoreParser<T> {
	
	private Class<T> clazz;
	
	private HashMap<String, Field> fieldsWithKeys;
	
	private Constructor<T> constructor;
	
	private Hstore hstore;
	
	/**
	 * 
	 * @param clazz
	 * @throws HstoreParseException 
	 */
	public HstoreParser(Class<T> clazz, Hstore hstore) throws HstoreParseException {
		this.clazz = clazz;
		this.fieldsWithKeys = new HashMap<>();
		this.hstore = hstore;
		//search all fields of all classes for annotated items
		initFields(clazz);
		
		//find empty constructor
		try {
			constructor = clazz.getConstructor();
		} catch (NoSuchMethodException | SecurityException e) {
			throw new HstoreParseException(HstoreParseException.HSTORE_CONSTRUCTOR_EXCEPTION + clazz.getCanonicalName(), e);
		}
	}

	/**
	 * 
	 * @param clazz
	 * @throws HstoreParseException 
	 */
	private void initFields(Class<?> clazz) throws HstoreParseException {
		if (clazz.getSuperclass() != null) {
			initFields(clazz.getSuperclass());
		}
		
		for (Field f : clazz.getDeclaredFields()) {
			if (f.isAnnotationPresent(HstoreKey.class)) {
				//annotation: HstoreKey was found
				String hstoreKey = ((HstoreKey) f.getAnnotation(HstoreKey.class)).value();
				//check for duplicates:
				if (!fieldsWithKeys.containsKey(hstoreKey)) {
					fieldsWithKeys.put(hstoreKey, f);
				}
				else {
					throw new HstoreParseException(HstoreParseException.HSTORE_OBJECT_KEY_ALREADY_IN_USE + hstoreKey);
				}
			}
			
			if (f.isAnnotationPresent(HstoreCollection.class)) {
				//annotation: HstoreCollection was found
				Class<?> hstoreClazz = ((HstoreCollection) f.getAnnotation(HstoreCollection.class)).value();
				//insert class into hstore
				if (!hstore.getParserMap().containsKey(hstoreClazz)) {
					hstore.addClass(hstoreClazz);
				}
			}
		}
	}
	
	/**
	 * 
	 * @param hstore
	 * @return
	 * @throws HstoreParseException
	 * @throws JSONException
	 */
	public T fromString(String hstore) throws HstoreParseException {
		try {
			return fromJson(new JSONObject("{" + hstore.replace("=>", ":") + "}"));
		} catch (JSONException e) {
			throw new HstoreParseException(HstoreParseException.HSTORE_FORMAT_UNSUPPORTED + hstore, e);
		}
	}
		
	/**
	 * 
	 * @param hstore
	 * @return
	 * @throws HstoreParseException 
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public T fromJson(JSONObject json) throws HstoreParseException {
		T object;
		try {
			object = constructor.newInstance();
		} catch (InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException e) {
			throw new HstoreParseException(HstoreParseException.HSTORE_PARSE_EXCEPTION + e.getLocalizedMessage(), e);
		}
		
		if (object == null) {
			throw new HstoreParseException(HstoreParseException.HSTORE_COULD_NOT_INSTANTIATE + clazz.getCanonicalName());
		}
		
		//iterate through all json entries:
		Iterator<String> jsonKeys = json.keys();
		while (jsonKeys.hasNext()) {
			String key = jsonKeys.next();

			//if fieldsWithKeys contain a key (=it was annotated with @HstoreKey) then try to set the value of the field in T object
			if (fieldsWithKeys.containsKey(key)) {
				try {
					Object value = json.get(key);
					Field field = fieldsWithKeys.get(key);
					field.setAccessible(true);
					//cast / new instance needed?
					if (field.isAnnotationPresent(HstoreCollection.class)) {
						Class<?> fieldClazz = field.getType();
						//System.out.println("FieldClazz: " + fieldClazz + " -> " + fieldClazz.isAssignableFrom(Collection.class));
						if (Collection.class.isAssignableFrom(fieldClazz)) {
							//get collection and instantiate if necessary
							Collection collection = (Collection) field.get(object);
							if (collection == null) {
								collection = (Collection) fieldClazz.newInstance();
							}
							
							ParameterizedType fieldType = (ParameterizedType) field.getGenericType();
						    Class<?> genericClazz = (Class<?>) fieldType.getActualTypeArguments()[0];
						    //System.out.println("genericClazz: " + genericClazz);
						    
						    //Object jsonObject = hstore.toJson((String) value, genericClazz);
						    Object jsonObject = null;
						    //System.out.println(value);
						    if (!value.equals(JSONObject.NULL)) {
						    	jsonObject = hstore.toJson((String) value, genericClazz);
						    }
						    else {
						    	jsonObject = hstore.toJson(null, genericClazz);
						    }
						    
						    if (jsonObject != null) {
							    if (jsonObject instanceof JSONArray) {
							    	//System.out.println(jsonObject);
							    	Object[] array = hstore.fromJSONArray((JSONArray) jsonObject, genericClazz);
								    for (int i = 0; i < array.length; i++) {
								    	//System.out.println("Created element: " + array[i]);
								    	collection.add(array[i]);
								    }
							    }						    	
							    else {
							    	Object element = hstore.fromString((String) value, genericClazz);
							    	//System.out.println("Created element: " + element);
							    	collection.add(element);
							    }						    	
						    }
						    
						    value = collection;
						}
						else {
							throw new HstoreParseException(HstoreParseException.HSTORE_MUST_BE_A_COLLECTION + field + " - " + clazz.getCanonicalName());
						}
					}
					
					if (field.isAnnotationPresent(HstoreCast.class)) {
						HstoreCast castDef = field.getAnnotation(HstoreCast.class);
						if (castDef.simpleCast()) {
							//simple cast
							field.set(object, castDef.clazz().cast(value));							
						}
						else {
							//new instance
							field.set(object, castDef.clazz().getConstructor(castDef.constructorParamClazz()).newInstance(value));
						}
					}
					else {
						if (JSONObject.NULL.equals(value)) {
							field.set(object, null);
						}
						else {
							field.set(object, value);
						}
					}

				} catch (IllegalAccessException | IllegalArgumentException | JSONException e) {
					throw new HstoreParseException(HstoreParseException.HSTORE_COULD_NOT_INSTANTIATE + clazz.getCanonicalName(), e);
				} catch (InstantiationException e) {
					throw new HstoreParseException(HstoreParseException.HSTORE_COULD_NOT_INSTANTIATE + clazz.getCanonicalName(), e);
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		return object;
	}

	/**
	 * 
	 * @param hstoreKey
	 * @param object
	 * @return
	 * @throws HstoreParseException
	 */
	public <U> Object getValue(String hstoreKey, U object) throws HstoreParseException {
		Field field = fieldsWithKeys.get(hstoreKey);
		if (field != null) {
			try {
				field.setAccessible(true);
				return field.get(object);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new HstoreParseException(HstoreParseException.HSTORE_COULD_NOT_GET_VALUE + clazz.getCanonicalName() + "." + field.getName() + "\n", e);
			}
		}
		
		return null;
	}
	
	/**
	 * 
	 * @return
	 */
	public Set<Field> getAnnotatedFields() {
		return new HashSet<>(fieldsWithKeys.values());
	}
	
	/**
	 * 
	 * @return
	 * @throws HstoreParseException 
	 */
	public <U> Map<String, Object> getValueMap(U object) throws HstoreParseException {
		HashMap<String, Object> resultMap = new HashMap<>();
		for (Entry<String, Field> e : fieldsWithKeys.entrySet()) {
			try {
				e.getValue().setAccessible(true);
				resultMap.put(e.getKey(), e.getValue().get(object));
			} catch (Exception ex) {
				ex.printStackTrace();
				throw new HstoreParseException(HstoreParseException.HSTORE_COULD_NOT_GET_VALUE + clazz.getCanonicalName() + "." + e.getValue().getName() + "\n", ex);
			}
		}
		
		return resultMap;
	}
	
	/**
	 * parses a simple hstore String to a JSONObject
	 * @param hstore
	 * @return {@link JSONObject} or null if an error occured
	 */
	public static JSONObject parseToJson(String hstore) {
		try {
			return new JSONObject("{" + hstore.replace("=>", ":") + "}");
		} catch (JSONException e) {
			return null;
		}
	}
}
