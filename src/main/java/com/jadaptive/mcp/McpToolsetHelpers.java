package com.jadaptive.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;

public class McpToolsetHelpers {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };


    static volatile boolean debugRequests;
    static volatile boolean stdioMode;
    
	public static McpSchema.Tool tool(String name, String description, String schemaJson) {
	    return McpSchema.Tool.builder()
	            .name(name)
	            .description(description)
	            .inputSchema(jsonSchema(schemaJson))
	            .build();
	}

	public static McpSchema.JsonSchema jsonSchema(String schemaJson) {
	    try {
	        Map<String, Object> root = MAPPER.readValue(schemaJson, MAP_TYPE);
	        return new McpSchema.JsonSchema(
	                asString(root.get("type")),
	                asMap(root.get("properties")),
	                asStringList(root.get("required")),
	                asBoolean(root.get("additionalProperties")),
	                asMap(root.get("$defs")),
	                asMap(root.get("definitions")));
	    }
	    catch (DatabindException e) {
	        throw new IllegalArgumentException("Invalid tool schema JSON.", e);
	    }
	}

	public static Boolean asBoolean(Object value) {
	    if (value == null) {
	        return null;
	    }
	    if (value instanceof Boolean b) {
	        return b;
	    }
	    return Boolean.parseBoolean(String.valueOf(value));
	}

	public static String asJson(Object value) {
	    try {
	        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
	    }
	    catch (DatabindException e) {
	        return String.valueOf(value);
	    }
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> asMap(Object value) {
	    if (value == null) {
	        return Collections.emptyMap();
	    }
	    if (value instanceof Map<?, ?> map) {
	        return (Map<String, Object>) map;
	    }
	    throw new IllegalArgumentException("Schema field must be an object.");
	}

	public static String asString(Object value) {
	    return value == null ? null : String.valueOf(value);
	}

	@SuppressWarnings("unchecked")
	public static List<String> asStringList(Object value) {
	    if (value == null) {
	        return List.of();
	    }
	    if (value instanceof List<?> list) {
	        return (List<String>) list;
	    }
	    throw new IllegalArgumentException("Schema field must be an array.");
	}

	public static boolean boolArg(Map<String, Object> args, String key, boolean fallback) {
	    Object value = args.get(key);
	    if (value == null) {
	        return fallback;
	    }
	    if (value instanceof Boolean b) {
	        return b;
	    }
	    return Boolean.parseBoolean(String.valueOf(value));
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> castMap(Map<?, ?> map) {
	    return (Map<String, Object>) map;
	}

	public static Map<String, Object> toolArgs(McpSchema.CallToolRequest request) {
	    Map<String, Object> arguments = request.arguments();
	    Map<String, Object> toolArguments = arguments == null ? Collections.emptyMap() : arguments;
	    debugToolRequest(request, toolArguments);
	    return toolArguments;
	}

	private static void debugToolRequest(McpSchema.CallToolRequest request, Map<String, Object> arguments) {
	    if (!debugRequests) {
	        return;
	    }
	
	    String transport = stdioMode ? "STDIO" : "HTTP";
	    String requestSummary = request == null ? "<null>" : request.toString();
	    String argsSummary = asJson(redacted(arguments));
	    System.err.println("[maverick-mcp][" + transport + "] tool request=" + requestSummary + " arguments=" + argsSummary);
	}

	public  static Map<String, Object> redacted(Map<String, Object> source) {
	    if (source == null || source.isEmpty()) {
	        return Collections.emptyMap();
	    }
	
	    Map<String, Object> copy = new LinkedHashMap<>();
	    for (Map.Entry<String, Object> entry : source.entrySet()) {
	        String key = entry.getKey();
	        Object value = entry.getValue();
	        if (isSensitiveKey(key)) {
	            copy.put(key, "<redacted>");
	        }
	        else if (value instanceof Map<?, ?> nestedMap) {
	            copy.put(key, redacted(castMap(nestedMap)));
	        }
	        else {
	            copy.put(key, value);
	        }
	    }
	    return copy;
	}

	public  static boolean isSensitiveKey(String key) {
	    if (key == null) {
	        return false;
	    }
	    String normalized = key.toLowerCase();
	    return normalized.contains("password")
	            || normalized.contains("passphrase")
	            || normalized.contains("token")
	            || normalized.contains("secret")
	            || normalized.contains("privatekey");
	}

	public  static int intArg(Map<String, Object> args, String key, int fallback) {
	    Object value = args.get(key);
	    if (value == null) {
	        return fallback;
	    }
	    if (value instanceof Number number) {
	        return number.intValue();
	    }
	    String s = String.valueOf(value);
	    if (s.isBlank()) {
	        return fallback;
	    }
	    return Integer.parseInt(s);
	}

	public static long longArg(Map<String, Object> args, String key, long fallback) {
	    Object value = args.get(key);
	    if (value == null) {
	        return fallback;
	    }
	    if (value instanceof Number number) {
	        return number.longValue();
	    }
	    String s = String.valueOf(value);
	    if (s.isBlank()) {
	        return fallback;
	    }
	    return Long.parseLong(s);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> mapArg(Map<String, Object> args, String key) {
	    Object value = args.get(key);
	    if (value == null) {
	        return Collections.emptyMap();
	    }
	    if (!(value instanceof Map<?, ?> map)) {
	        throw new IllegalArgumentException("Argument must be an object: " + key);
	    }
	    return (Map<String, Object>) map;
	}

	public static String stringArg(Map<String, Object> args, String key, boolean required) {
	    Object value = args.get(key);
	    if (value == null) {
	        if (required) {
	            throw new IllegalArgumentException("Missing required argument: " + key);
	        }
	        return null;
	    }
	    String str = String.valueOf(value);
	    if (required && str.isBlank()) {
	        throw new IllegalArgumentException("Blank argument: " + key);
	    }
	    return str;
	}

	public static String defaultString(String value, String fallback) {
	    return value == null || value.isBlank() ? fallback : value;
	}

	public static McpSchema.CallToolResult error(String message) {
	    return McpSchema.CallToolResult.builder()
	            .isError(true)
	            .addTextContent(message)
	            .build();
	}

	public static McpSchema.CallToolResult ok(Map<String, Object> payload) {
	    return McpSchema.CallToolResult.builder()
	            .structuredContent(payload)
	            .addTextContent(asJson(payload))
	            .isError(false)
	            .build();
	}

}
