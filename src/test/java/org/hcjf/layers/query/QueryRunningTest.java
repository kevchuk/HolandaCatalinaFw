package org.hcjf.layers.query;

import org.hcjf.layers.Layers;
import org.hcjf.layers.query.functions.BaseQueryFunctionLayer;
import org.hcjf.layers.query.functions.QueryFunctionLayerInterface;
import org.hcjf.properties.SystemProperties;
import org.hcjf.service.Service;
import org.hcjf.service.ServiceSession;
import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author javaito
 */
public class QueryRunningTest {

    private static final String CHARACTER = "character";
    private static final String ADDRESS = "address";

    private static final String ID = "id";

    private static final String NAME = "name";
    private static final String LAST_NAME = "lastName";
    private static final String NICKNAME = "nickname";
    private static final String BIRTHDAY = "birthday";
    private static final String WEIGHT = "weight";
    private static final String HEIGHT = "height";
    private static final String GENDER = "gender";
    private static final String ADDRESS_ID = "addressId";

    private static final String STREET = "street";
    private static final String NUMBER = "number";

    private static final Map<UUID, JoinableMap> simpsonCharacters = new HashMap<>();
    private static final Map<UUID, JoinableMap> simpsonAddresses = new HashMap<>();
    private static final TestDataSource dataSource = new TestDataSource();

    static {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            JoinableMap map = new JoinableMap(ADDRESS);
            UUID addressId = UUID.randomUUID();
            map.put(ADDRESS_ID, addressId);
            map.put(STREET, "Evergreen Terrace");
            map.put(NUMBER, 742);
            simpsonAddresses.put(addressId, map);

            map = new JoinableMap(CHARACTER);
            map.put(ID, UUID.randomUUID());
            map.put(NAME, "Homer Jay");
            map.put(LAST_NAME, "Simpson");
            map.put(BIRTHDAY, dateFormat.parse("1981-02-19"));
            map.put(WEIGHT, 108.0);
            map.put(HEIGHT, 1.83);
            map.put(GENDER, Gender.MALE);
            map.put(ADDRESS_ID, addressId);
            simpsonCharacters.put((UUID) map.get(ID), map);

            map = new JoinableMap(CHARACTER);
            map.put(ID, UUID.randomUUID());
            map.put(NAME, "Marjorie Jaqueline");
            map.put(LAST_NAME, "Bouvier Simpson");
            map.put(NICKNAME, "Marge");
            map.put(BIRTHDAY, dateFormat.parse("1981-03-14"));
            map.put(WEIGHT, 67.0);
            map.put(HEIGHT, 1.65);
            map.put(GENDER, Gender.FEMALE);
            map.put(ADDRESS_ID, addressId);
            simpsonCharacters.put((UUID) map.get(ID), map);

            map = new JoinableMap(CHARACTER);
            map.put(ID, UUID.randomUUID());
            map.put(NAME, "Bartolomeo Jay");
            map.put(LAST_NAME, "Simpson Bouvier");
            map.put(NICKNAME, "Bart");
            map.put(BIRTHDAY, dateFormat.parse("2007-03-14"));
            map.put(WEIGHT, 45.0);
            map.put(HEIGHT, 1.20);
            map.put(GENDER, Gender.MALE);
            map.put(ADDRESS_ID, addressId);
            simpsonCharacters.put((UUID) map.get(ID), map);

            map = new JoinableMap(CHARACTER);
            map.put(ID, UUID.randomUUID());
            map.put(NAME, "Lisa Marie");
            map.put(LAST_NAME, "Simpson Bouvier");
            map.put(BIRTHDAY, dateFormat.parse("2009-07-20"));
            map.put(WEIGHT, 37.0);
            map.put(HEIGHT, 1.05);
            map.put(GENDER, Gender.FEMALE);
            map.put(ADDRESS_ID, addressId);
            simpsonCharacters.put((UUID) map.get(ID), map);

            map = new JoinableMap(CHARACTER);
            map.put(ID, UUID.randomUUID());
            map.put(NAME, "Margaret Abigail");
            map.put(LAST_NAME, "Simpson Bouvier");
            map.put(NICKNAME, "Maggie");
            map.put(BIRTHDAY, dateFormat.parse("2015-09-02"));
            map.put(WEIGHT, 15.0);
            map.put(HEIGHT, 0.75);
            map.put(GENDER, Gender.FEMALE);
            map.put(ADDRESS_ID, addressId);
            simpsonCharacters.put((UUID) map.get(ID), map);

            map = new JoinableMap(ADDRESS);
            addressId = UUID.randomUUID();
            map.put(ADDRESS_ID, addressId);
            map.put(STREET, "Buenos Aires");
            map.put(NUMBER, 1025);
            simpsonAddresses.put(addressId, map);

            map = new JoinableMap(CHARACTER);
            map.put(ID, UUID.randomUUID());
            map.put(NAME, "Maurice Lester");
            map.put(LAST_NAME, "Szyslak");
            map.put(NICKNAME, "Moe");
            map.put(BIRTHDAY, dateFormat.parse("1975-03-14"));
            map.put(WEIGHT, 82.0);
            map.put(HEIGHT, 1.70);
            map.put(GENDER, Gender.MALE);
            map.put(ADDRESS_ID, addressId);
            simpsonCharacters.put((UUID) map.get(ID), map);
        } catch (Exception ex){}
    }

    public enum Gender {

        MALE,

        FEMALE

    }

    private static class TestDataSource implements Query.DataSource<JoinableMap> {

        @Override
        public Collection<JoinableMap> getResourceData(Query query) {
            Collection<JoinableMap> result = new HashSet<>();

            switch (query.getResourceName()) {
                case CHARACTER: {
                    for(JoinableMap map : simpsonCharacters.values()) {
                        result.add(new JoinableMap(map));
                    }
                    break;
                }
                case ADDRESS: {
                    for(JoinableMap map : simpsonAddresses.values()) {
                        result.add(new JoinableMap(map));
                    }
                    break;
                }
                default:{
                    throw new IllegalArgumentException("Resource not found " + query.getResourceName());
                }
            }

            return result;
        }

    }

    @Test
    public void select() {
        Service.run(() -> {

            Query query = Query.compile("SELECT * FROM character");
            Set<JoinableMap> resultSet = query.evaluate(dataSource);
            Assert.assertEquals(resultSet.size(), simpsonCharacters.size());

            query = Query.compile("SELECT * FROM character ORDER BY addressId, name DESC");
            resultSet = query.evaluate(dataSource);
            System.out.println();

            query = Query.compile("SELECT now(), getYear(birthday), periodInDays(birthday), getMonth(birthday) FROM character");
            resultSet = query.evaluate(dataSource);
            Assert.assertEquals(resultSet.size(), simpsonCharacters.size());

            query = Query.compile("SELECT * FROM character GROUP BY getMonth(birthday)");
            resultSet = query.evaluate(dataSource);

            query = Query.compile("SELECT weight, 2  *  weight AS superWeight, pow(max(integerValue(weight), integerValue(50.1)) ,2) AS smartWeight FROM character");
            resultSet = query.evaluate(dataSource);
            for(JoinableMap joinableMap : resultSet) {
                if(((Number)joinableMap.get(WEIGHT)).doubleValue() > 50) {
                    Assert.assertTrue(((Number)joinableMap.get("smartWeight")).doubleValue() > 2500);
                } else {
                    Assert.assertTrue(((Number)joinableMap.get("smartWeight")).intValue() == 2500);
                }
            }

            query = Query.compile("SELECT weight, 2  *  weight AS superWeight, pow(max(weight, 50.1) ,2) AS smartWeight FROM character");
            resultSet = query.evaluate(dataSource);

            query = Query.compile("SELECT name, nickname FROM character");
            resultSet = query.evaluate(dataSource);
            Assert.assertEquals(resultSet.iterator().next().size(), 2);

            query = Query.compile("SELECT *, name as nombre FROM character");
            resultSet = query.evaluate(dataSource);
            JoinableMap first = resultSet.iterator().next();
            Assert.assertEquals(first.get("nombre"), first.get("name"));

            query = Query.compile("SELECT street, concat(name), stringJoin('&', name), sum(weight) FROM character JOIN address ON address.addressId = character.addressId GROUP BY addressId");
            resultSet = query.evaluate(dataSource);
            Assert.assertEquals(resultSet.size(), simpsonAddresses.size());


            Layers.publishLayer(CustomFunciton.class);

            query = Query.compile("SELECT name, customFunction(integerValue(weight)) FROM character");
            resultSet = query.evaluate(dataSource);
            System.out.println();

        }, ServiceSession.getSystemSession(), true, 0);
    }

    public static class CustomFunciton extends BaseQueryFunctionLayer implements QueryFunctionLayerInterface {

        public CustomFunciton() {
            super(SystemProperties.get(SystemProperties.Query.Function.NAME_PREFIX) + "customFunction");
        }

        @Override
        public Object evaluate(String functionName, Object... parameters) {
            return ((Integer)parameters[0]) % 2 == 0 ? "P" : "I";
        }
    }
}
