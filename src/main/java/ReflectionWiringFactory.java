import graphql.language.*;
import graphql.language.Type;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.InterfaceWiringEnvironment;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.WiringFactory;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class ReflectionWiringFactory implements WiringFactory {

    private final String resolverPackage;
    private final List<String> errors;
    private final Map<String, Set<Class>> scalarTypeMap;
    private final Map<String, Class> objectTypeMap;

    public List<String> getErrors() {
        return errors;
    }

    public ReflectionWiringFactory(String resolverPackage, TypeDefinitionRegistry registry) {
        this.resolverPackage = resolverPackage;
        errors = new ArrayList<>();
        objectTypeMap = new HashMap<>();
        scalarTypeMap = new HashMap<>();

        scalarTypeMap.put("Boolean", new HashSet<>(Arrays.asList(Boolean.class, boolean.class)));
        scalarTypeMap.put("Int", new HashSet<>(Arrays.asList(Integer.class, int.class)));
        scalarTypeMap.put("Float", new HashSet<>(Arrays.asList(Float.class, float.class, Double.class, double.class)));
        scalarTypeMap.put("String", new HashSet<>(Arrays.asList(String.class)));
        scalarTypeMap.put("ID", new HashSet<>(Arrays.asList(String.class)));

        for (String typeName : registry.types().keySet()) {
            Class c = findTypeClass(typeName);
            if (c != null) {
                objectTypeMap.put(typeName, c);
            }
        }
    }

    @Override
    public boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
        return false;
    }

    @Override
    public boolean providesDataFetcher(FieldWiringEnvironment env) {
        Class typeClass = objectTypeMap.get(env.getParentType().getName());
        if (typeClass == null) {
            return false;
        }

        Method fetcherMethod = findFetcherMethod(env.getFieldDefinition(), typeClass);
        if (fetcherMethod != null) {
            return true;
        } else {
            fetcherMethod = findGetter(env.getFieldDefinition(), typeClass);
            if (fetcherMethod != null) {
                return true;
            } else {
                error("Unable to find resolver for field '%s' of type '%s'",
                        env.getFieldDefinition().getName(), env.getParentType().getName());
                return false;
            }
        }
    }

    @Override
    public DataFetcher getDataFetcher(FieldWiringEnvironment env) {
        Class typeClass = objectTypeMap.get(env.getParentType().getName());
        Method fetcherMethod = findFetcherMethod(env.getFieldDefinition(), typeClass);
        if (fetcherMethod != null) {
            return buildDataFetcherFromMethod(fetcherMethod, env.getFieldDefinition().getInputValueDefinitions());
        } else {
            Method getterMethod = findGetter(env.getFieldDefinition(), typeClass);
            return buildDataFetcherFromGetter(getterMethod);
        }
    }

    private Class findTypeClass(String name) {
        String className = resolverPackage + "." + name;
        try {
            Class typeClass = Class.forName(className);
            if (!Modifier.isPublic(typeClass.getModifiers())) {
                error("Class '%s' is not public", typeClass.getName());
                return null;
            }
            return typeClass;
        } catch (ClassNotFoundException e) {
            error("Class '%s' not found", className);
            return null;
        }
    }

    private Method findFetcherMethod(FieldDefinition fieldDefinition, Class cls) {
        String fetcherName = buildFetcherName("fetch", fieldDefinition);

        Method method = findPublicMethod(cls, fetcherName, fieldDefinition.getType());

        if (method == null) {
            return null;
        }

        List<Parameter> methodParams = new ArrayList<>(Arrays.asList(method.getParameters()));

        if (methodParams.isEmpty()) {
            error("Method '%s' in class '%s' doesn't have DataFetchingEnvironment as first parameter",
                    fetcherName, cls.getName());
            return null;
        }

        Parameter envParam = methodParams.remove(0);

        if (!envParam.getType().equals(DataFetchingEnvironment.class)) {
            error("Method '%s' in class '%s' doesn't have DataFetchingEnvironment as first parameter",
                    fetcherName, cls.getName());
            return null;
        }

        List<InputValueDefinition> fieldParams = fieldDefinition.getInputValueDefinitions();

        if (methodParams.size() != fieldParams.size()) {
            error("Method '%s' in class '%s' doesn't have the right number of arguments",
                    fetcherName, cls.getName());
            return null;
        }

        for (int i = 0; i < methodParams.size(); i++) {
            Parameter param = methodParams.get(i);
            InputValueDefinition inputDef = fieldParams.get(i);
            if (!isTypeCompatible(inputDef.getType(), param.getType(), param.getAnnotatedType())) {
                error("Type mismatch in method '%s', argument '%d' in class '%s' " +
                                "expected '%s', got '%s'",
                        fetcherName, i + 1, cls.getName(), inputDef.getType().toString(),
                        param.getType().getName());
                return null;
            }
        }

        return method;
    }

    private Method findGetter(FieldDefinition fieldDefinition, Class cls) {
        String fetcherName = buildFetcherName("get", fieldDefinition);
        Method getter = findPublicMethod(cls, fetcherName, fieldDefinition.getType());

        if (getter == null && isTypeCompatible(fieldDefinition.getType(), Boolean.class)) {
            fetcherName = buildFetcherName("is", fieldDefinition);
            getter = findPublicMethod(cls, fetcherName, fieldDefinition.getType());
        }
        return getter;
    }

    private Method findPublicMethod(Class cls, String methodName, Type fieldReturnType) {
        List<Method> matchingMethods = Arrays.stream(cls.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .collect(Collectors.toList());

        if (matchingMethods.size() == 0) {
            return null;
        }

        if (matchingMethods.size() > 1) {
            error("Overloaded '%s' method not allowed in class '%s'", methodName, cls.getName());
            return null;
        }

        Method method = matchingMethods.get(0);

        if (!Modifier.isPublic(method.getModifiers())) {
            error("Method '%s' in class '%s' is not public", methodName, cls.getName());
            return null;
        }

        if (!isTypeCompatible(fieldReturnType, method.getReturnType(), method.getAnnotatedReturnType())) {
            error("Method '%s' in class '%s' returns '%s' instead of expected '%s'",
                    methodName, cls.getName(), method.getReturnType().getName(), fieldReturnType);
            return null;
        }

        return method;
    }

    private boolean isTypeCompatible(Type graphqlType, Class<?> javaType) {
        return isTypeCompatible(graphqlType, javaType, null);
    }

    private boolean isTypeCompatible(Type graphqlType, Class<?> javaType, AnnotatedType javaAnnotatedType) {
        if (graphqlType instanceof TypeName) {
            String typeName = ((TypeName) graphqlType).getName();
            if (scalarTypeMap.containsKey(typeName)) {
                return scalarTypeMap.getOrDefault(typeName, Collections.emptySet()).contains(javaType);
            } else if (objectTypeMap.containsKey(typeName)) {
                return objectTypeMap.get(typeName) == javaType;
            }
        } else if (graphqlType instanceof ListType) {
            if (!List.class.isAssignableFrom(javaType)) {
                return false;
            }
            if (javaAnnotatedType == null || !(javaAnnotatedType instanceof AnnotatedParameterizedType)) {
                return false;
            }
            AnnotatedParameterizedType parameterizedType = (AnnotatedParameterizedType) javaAnnotatedType;
            if (parameterizedType.getAnnotatedActualTypeArguments().length != 1) {
                return false;
            }

            Class<?> javaInnerType = (Class<?>) parameterizedType.getAnnotatedActualTypeArguments()[0].getType();
            Type graphqlInnerType = ((ListType)graphqlType).getType();
            return isTypeCompatible(graphqlInnerType, javaInnerType);
        }

        return false;
    }

    private DataFetcher buildDataFetcherFromMethod(Method method, List<InputValueDefinition> fieldParams) {
        return env -> {
            if (!Modifier.isStatic(method.getModifiers()) && env.getSource() == null) {
                throw new RuntimeException("DataFetcher method doesn't have source.");
            }

            List parameters = new ArrayList();
            parameters.add(env);
            for (InputValueDefinition fieldParam : fieldParams) {
                parameters.add(env.getArgument(fieldParam.getName()));
            }
            try {
                return method.invoke(env.getSource(), parameters.toArray());
            } catch (Exception e) {
                throw new RuntimeException("Error invoking data fetcher", e);
            }
        };
    }

    private DataFetcher buildDataFetcherFromGetter(Method getter) {
        return env -> {
            Object source = env.getSource();
            if (!Modifier.isStatic(getter.getModifiers()) && env.getSource() == null) {
                throw new RuntimeException("Getter DataFetcher doesn't have source.");
            }
            try {
                return getter.invoke(source);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking data fetcher: " + e.toString(), e);
            }
        };
    }

    private String buildFetcherName(String prefix, FieldDefinition fieldDefinition) {
        String fieldName = fieldDefinition.getName();
        return prefix + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    private void error(String message, Object... args) {
        errors.add(String.format(message, args));
    }
}