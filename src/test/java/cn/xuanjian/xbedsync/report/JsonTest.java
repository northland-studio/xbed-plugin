package cn.xuanjian.xbedsync.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void escapesSpecialCharacters() {
        assertEquals("a\\\"b\\\\c\\nd\\te", Json.escape("a\"b\\c\nd\te"));
        assertEquals("\\u0001", Json.escape("\u0001"));
        assertEquals("中文红队", Json.escape("中文红队"));
    }

    @Test
    void buildsObjectWithNullAndRawValues() {
        Json.Obj nested = Json.object().put("k", 1);
        Json.Arr array = Json.array().add("a").add(2).add((String) null);
        String json = Json.object()
                .put("schemaVersion", 1)
                .put("name", "红队")
                .put("missing", (String) null)
                .put("flag", true)
                .putRaw("nested", nested.end())
                .putRaw("list", array.end())
                .end();
        assertEquals("{\"schemaVersion\":1,\"name\":\"红队\",\"missing\":null,\"flag\":true,"
                + "\"nested\":{\"k\":1},\"list\":[\"a\",2,null]}", json);
    }

    @Test
    void rejectsWritesAfterClose() {
        Json.Obj obj = Json.object();
        obj.end();
        try {
            obj.put("late", 1);
            throw new AssertionError("应当抛出 IllegalStateException");
        } catch (IllegalStateException expected) {
            // ok
        }
    }
}
