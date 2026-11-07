package cn.xuanjian.xbedsync.report;

/**
 * 极简 JSON 写出工具（不引入任何第三方依赖，兼容 Java 8 / Bukkit 1.8.8）。
 *
 * <p>只实现「写」：插件需要把比赛数据序列化为 JSON，读取只做极少量的字符串匹配，
 * 因此不实现 JSON 解析器，避免把不必要的复杂度带进生产环境。</p>
 *
 * <p>用法：</p>
 * <pre>
 * String json = Json.object()
 *         .put("schemaVersion", 1)
 *         .put("matchId", id)
 *         .put("abnormalReason", (String) null)
 *         .end();
 * </pre>
 */
public final class Json {

    private Json() {
    }

    public static Obj object() {
        return new Obj();
    }

    public static Arr array() {
        return new Arr();
    }

    /**
     * 转义 JSON 字符串内容（返回不含两侧引号的内容）。
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == 0x7f) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int pad = hex.length(); pad < 4; pad++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * JSON 对象构造器。
     */
    public static final class Obj {
        private final StringBuilder sb = new StringBuilder(256);
        private boolean first = true;
        private boolean closed;

        private Obj() {
            sb.append('{');
        }

        private void key(String name) {
            if (closed) {
                throw new IllegalStateException("JSON object already closed");
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(name)).append("\":");
        }

        public Obj put(String name, String value) {
            key(name);
            if (value == null) {
                sb.append("null");
            } else {
                sb.append('"').append(escape(value)).append('"');
            }
            return this;
        }

        public Obj put(String name, int value) {
            key(name);
            sb.append(value);
            return this;
        }

        public Obj put(String name, Integer value) {
            if (value == null) {
                return putNull(name);
            }
            key(name);
            sb.append(value.intValue());
            return this;
        }

        public Obj put(String name, boolean value) {
            key(name);
            sb.append(value);
            return this;
        }

        public Obj putNull(String name) {
            key(name);
            sb.append("null");
            return this;
        }

        /**
         * 写入已经序列化好的 JSON 片段（对象/数组），调用方负责保证其合法。
         */
        public Obj putRaw(String name, String rawJson) {
            key(name);
            sb.append(rawJson);
            return this;
        }

        public String end() {
            if (!closed) {
                sb.append('}');
                closed = true;
            }
            return sb.toString();
        }
    }

    /**
     * JSON 数组构造器。
     */
    public static final class Arr {
        private final StringBuilder sb = new StringBuilder(128);
        private boolean first = true;
        private boolean closed;

        private Arr() {
            sb.append('[');
        }

        public Arr add(String value) {
            if (closed) {
                throw new IllegalStateException("JSON array already closed");
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            if (value == null) {
                sb.append("null");
            } else {
                sb.append('"').append(escape(value)).append('"');
            }
            return this;
        }

        public Arr add(int value) {
            if (closed) {
                throw new IllegalStateException("JSON array already closed");
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(value);
            return this;
        }

        public Arr addRaw(String rawJson) {
            if (closed) {
                throw new IllegalStateException("JSON array already closed");
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(rawJson);
            return this;
        }

        public String end() {
            if (!closed) {
                sb.append(']');
                closed = true;
            }
            return sb.toString();
        }
    }
}
