import graphql.language.*;
import graphql.language.Type;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReflectionWiringFactory implements WiringFactory {

    private final List<String> errors = new ArrayList<>();
    private final Map<String, Set<Class<?>>> scalarTypeMap = new HashMap<>();
    private final Map<String, Class<?>> objectTypeMap = new HashMap<>();
    private final Map<String, Class<?>> inputObjectTypeMap = new HashMap<>();
    private final Map<String, Class<?>> enumTypeMap = new HashMap<>();
    private final Map<String, Class<?>> interfaceTypeMap = new HashMap<>();
    private final Map<String, Set<String>> interfacesImplemented = new HashMap<>();
    private final Map<String, Map<String, Method>> resolverMap = new HashMap<>();

    public ReflectionWiringFactory(TypeDefinitionRegistry registry, String packageName) {
        Map<String, Class<?>> classes = new HashMap<>();
        for (TypeDefinition typeDef : registry.types().values()) {
            String className = packageName + "." + typeDef.getName();
            try {
                classes.put(typeDef.getName(), Class.forName(className));
            } catch (ClassNotFoundException e) {
            }
        }
        registerTypes(registry.types().values(), classes);
        verifyClasses(registry.types().values());
    }

    public ReflectionWiringFactory(TypeDefinitionRegistry registry, Collection<Class<?>> classList) {
        Map<String, Class<?>> classes = classList.stream()
                .collect(Collectors.toMap(Class::getSimpleName, Function.identity()));
        registerTypes(registry.types().values(), classes);
        verifyClasses(registry.types().values());
    }

    @Override
    public boolean providesDataFetcher(FieldWiringEnvironment env) {
        String typeName = env.getParentType().getName();
        String fieldName = env.getFieldDefinition().getName();
        return resolverMap.containsKey(typeName) && resolverMap.get(typeName).containsKey(fieldName);
    }

    @Override
    public DataFetcher getDataFetcher(FieldWiringEnvironment env) {
        Method method = resolverMap.get(env.getParentType().getName()).get(env.getFieldDefinition().getName());

        if (method.getName().startsWith("fetch")) {
            return buildDataFetcherFromMethod(method, env.getFieldDefinition().getInputValueDefinitions());
        } else {
            return buildDataFetcherFromGetter(method);
        }
    }

    @Override
    public boolean providesTypeResolver(InterfaceWiringEnvironment env) {
        return interfaceTypeMap.containsKey(env.getInterfaceTypeDefinition().getName());
    }

    @Override
    public TypeResolver getTypeResolver(InterfaceWiringEnvironment env) {
        return buildTypeResolver(env.getInterfaceTypeDefinition().getName());
    }

    @Override
    public boolean providesTypeResolver(UnionWiringEnvironment env) {
        return interfaceTypeMap.containsKey(env.getUnionTypeDefinition().getName());
    }

    @Override
    public TypeResolver getTypeResolver(UnionWiringEnvironment env) {
        return buildTypeResolver(env.getUnionTypeDefinition().getName());
    }

    public List<String> getErrors() {
        return errors;
    }

    private void registerTypes(Collection<TypeDefinition> graphqlTypes, Map<String, Class<?>> classes) {

        scalarTypeMap.put("Boolean", new HashSet<>(Arrays.asList(Boolean.class, boolean.class)));
        scalarTypeMap.put("Int", new HashSet<>(Arrays.asList(Integer.class, int.class)));
        scalarTypeMap.put("Float", new HashSet<>(Arrays.asList(Double.class, double.class)));
        scalarTypeMap.put("String", new HashSet<>(Collections.singletonList(String.class)));
        scalarTypeMap.put("ID", new HashSet<>(Collections.singletonList(String.class)));

        for (TypeDefinition typeDef : graphqlTypes) {
            Class<?> javaClass = classes.get(typeDef.getName());

            if (javaClass == null) {
                error("Class for type '%s' was not found", typeDef.getName());
                continue;
            }

            if (!Modifier.isPublic(javaClass.getModifiers())) {
                error("Class '%s' is not public", javaClass.getSimpleName());
                continue;
            }

            if (typeDef instanceof ObjectTypeDefinition) {
                objectTypeMap.put(typeDef.getName(), javaClass);

                List<String> implementedInterfaces = ((ObjectTypeDefinition) typeDef).getImplements().stream()
                        .map(t -> typeToString(t))
                        .collect(Collectors.toList());
                if (implementedInterfaces.size() > 0) {
                    interfacesImplemented.putIfAbsent(typeDef.getName(), new HashSet<>());
                    interfacesImplemented.get(typeDef.getName()).addAll(implementedInterfaces);
                }
            } else if (typeDef instanceof InputObjectTypeDefinition) {
                inputObjectTypeMap.put(typeDef.getName(), javaClass);
            } else if (typeDef instanceof EnumTypeDefinition) {
                enumTypeMap.put(typeDef.getName(), javaClass);
            } else if (typeDef instanceof InterfaceTypeDefinition) {
                interfaceTypeMap.put(typeDef.getName(), javaClass);
            } else if (typeDef instanceof UnionTypeDefinition) {
                interfaceTypeMap.put(typeDef.getName(), javaClass);

                for (Type member : ((UnionTypeDefinition) typeDef).getMemberTypes()) {
                    String memberName = typeToString(member);
                    interfacesImplemented.putIfAbsent(memberName, new HashSet<>());
                    interfacesImplemented.get(memberName).add(typeDef.getName());
                }
            }
        }
    }

    private void verifyClasses(Collection<TypeDefinition> graphqlTypes) {
        for (TypeDefinition typeDef : graphqlTypes) {
            if (typeDef instanceof ObjectTypeDefinition) {
                verifyObjectType((ObjectTypeDefinition) typeDef);
            } else if (typeDef instanceof UnionTypeDefinition) {
                verifyUnionType((UnionTypeDefinition)typeDef);
            } else if (typeDef instanceof InputObjectTypeDefinition) {
                verifyInputObjectType((InputObjectTypeDefinition) typeDef);
            } else if (typeDef instanceof EnumTypeDefinition) {
                verifyEnumType((EnumTypeDefinition) typeDef);
            } else if (typeDef instanceof InterfaceTypeDefinition) {
                verifyInterfaceType((InterfaceTypeDefinition) typeDef);
            }
        }
    }

    private void verifyObjectType(ObjectTypeDefinition graphqlObjectTypeDef) {
        String typeName = graphqlObjectTypeDef.getName();
        Class<?> javaClass = objectTypeMap.get(typeName);

        if (javaClass == null) {
            return;
        }

        for (FieldDefinition fieldDef : graphqlObjectTypeDef.getFieldDefinitions()) {
            Method method = findCompatibleMethod(javaClass, fieldDef);

            if (method == null) {
                error("Unable to find resolver for field '%s' of type '%s'",
                        fieldDef.getName(), typeName);
                continue;
            }

            resolverMap.putIfAbsent(typeName, new HashMap<>());
            resolverMap.get(typeName).put(fieldDef.getName(), method);
        }

        for (String interfaceName : interfacesImplemented.getOrDefault(typeName, Collections.emptySet())) {
            Class<?> javaInterface = interfaceTypeMap.get(interfaceName);
            if (javaInterface == null || !javaInterface.isAssignableFrom(javaClass)) {
                error("Class '%s' does not implement interface '%s'",
                        javaClass.getSimpleName(), interfaceName);
            }
        }
    }

    private void verifyInterfaceType(InterfaceTypeDefinition graphqlInterfaceDef) {
        Class<?> javaInterface = interfaceTypeMap.get(graphqlInterfaceDef.getName());

        if (!javaInterface.isInterface()) {
            error("Class '%s' is not an interface but defined in GraphQL as interface",
                    javaInterface.getSimpleName());
            return;
        }

        for (FieldDefinition fieldDef : graphqlInterfaceDef.getFieldDefinitions()) {
            Method method = findCompatibleMethod(javaInterface, fieldDef);
            if (method == null) {
                error("Interface '%s' does not define properly define method '%s'",
                        javaInterface.getSimpleName(), fieldDef.getName());
                return;
            }
        }
    }

    private void verifyUnionType(UnionTypeDefinition graphqlUnionTypeDef) {
        Class<?> javaUnion = interfaceTypeMap.get(graphqlUnionTypeDef.getName());

        if (javaUnion == null) {
            return;
        }

        if (!javaUnion.isInterface()) {
            error("Class '%s' is not an interface but defined in GraphQL as Union",
                    javaUnion.getSimpleName());
            return;
        }

        if (javaUnion.getMethods().length != 0) {
            error("Interface '%s' should not have methods, it's mapped as a GraphQL Union",
                    javaUnion.getSimpleName());
        }
    }

    private void verifyEnumType(EnumTypeDefinition graphqlEnumDef) {
        Class<?> javaEnum = enumTypeMap.get(graphqlEnumDef.getName());

        if (javaEnum == null) {
            return;
        }

        if (!javaEnum.isEnum()) {
            error("Class '%s' is not Enum", javaEnum.getSimpleName());
            return;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Enum> e = (Class<? extends Enum>) javaEnum;
        Set<String> graphqlEnumNames = graphqlEnumDef.getEnumValueDefinitions().stream()
                .map(EnumValueDefinition::getName)
                .collect(Collectors.toSet());
        Set<String> javaEnumNames = Arrays.stream(e.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toSet());
        if (!graphqlEnumNames.equals(javaEnumNames)) {
            error("Java Enum '%s' doesn't have the same values as GraphQL Enum '%s'",
                    e.getSimpleName(), graphqlEnumDef.getName());
        }
    }

    private void verifyInputObjectType(InputObjectTypeDefinition graphqlInputObjectDef) {
        Class<?> javaClass = inputObjectTypeMap.get(graphqlInputObjectDef.getName());

        // TODO: Validate that input object has complete fields
        if (javaClass == null || findInputTypeConstructor(javaClass) == null) {
            return;
        }
    }

    private Method findCompatibleMethod(Class<?> javaClass, FieldDefinition graphqlFieldDef) {
        Method fetcherMethod = findFetcherMethod(javaClass, graphqlFieldDef);
        if (fetcherMethod != null) {
            return fetcherMethod;
        }

        Method getterMethod = findGetter(javaClass, graphqlFieldDef);
        if (getterMethod != null) {
            return getterMethod;
        }

        return null;
    }

    private Method findFetcherMethod(Class<?> javaClass, FieldDefinition graphqlFieldDef) {
        String fetcherName = buildFetcherName("fetch", graphqlFieldDef);

        Method method = findPublicMethod(javaClass, fetcherName, graphqlFieldDef.getType());

        if (method == null) {
            return null;
        }

        List<Parameter> methodParams = new ArrayList<>(Arrays.asList(method.getParameters()));

        if (methodParams.isEmpty()) {
            error("Method '%s' in class '%s' doesn't have DataFetchingEnvironment as first parameter",
                    fetcherName, javaClass.getSimpleName());
            return null;
        }

        Parameter envParam = methodParams.remove(0);

        if (!envParam.getType().equals(DataFetchingEnvironment.class)) {
            error("Method '%s' in class '%s' doesn't have DataFetchingEnvironment as first parameter",
                    fetcherName, javaClass.getSimpleName());
            return null;
        }

        List<InputValueDefinition> fieldParams = graphqlFieldDef.getInputValueDefinitions();

        if (methodParams.size() != fieldParams.size()) {
            error("Method '%s' in class '%s' doesn't have the right number of arguments",
                    fetcherName, javaClass.getSimpleName());
            return null;
        }

        for (int i = 0; i < methodParams.size(); i++) {
            Parameter param = methodParams.get(i);
            InputValueDefinition inputDef = fieldParams.get(i);
            // TODO: provide better error handling in case of using logs, shorts, floats, etc.
            if (!isTypeCompatible(inputDef.getType(), param.getType(), param.getAnnotatedType())) {
                error("Type mismatch in method '%s', argument '%d' in class '%s' " +
                                "expected '%s', got '%s'",
                        fetcherName, i + 1, javaClass.getSimpleName(), typeToString(inputDef.getType()),
                        param.getType().getSimpleName());
                return null;
            }
        }

        return method;
    }

    private Method findGetter(Class<?> javaClass, FieldDefinition fieldDefinition) {
        String fetcherName = buildFetcherName("get", fieldDefinition);
        Method getter = findPublicMethod(javaClass, fetcherName, fieldDefinition.getType());

        if (getter == null && isTypeCompatible(fieldDefinition.getType(), Boolean.class)) {
            fetcherName = buildFetcherName("is", fieldDefinition);
            getter = findPublicMethod(javaClass, fetcherName, fieldDefinition.getType());
        }
        return getter;
    }

    private Method findPublicMethod(Class<?> javaClass, String methodName, Type fieldReturnType) {
        List<Method> matchingMethods = Arrays.stream(javaClass.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .collect(Collectors.toList());

        if (matchingMethods.size() == 0) {
            return null;
        }

        if (matchingMethods.size() > 1) {
            error("Overloaded '%s' method not allowed in class '%s'", methodName, javaClass.getSimpleName());
            return null;
        }

        Method method = matchingMethods.get(0);

        if (!isTypeCompatible(fieldReturnType, method.getReturnType(), method.getAnnotatedReturnType())) {
            error("Method '%s' in class '%s' returns '%s' instead of expected '%s'",
                    methodName, javaClass.getSimpleName(), method.getReturnType().getSimpleName(), typeToString(fieldReturnType));
            return null;
        }

        return method;
    }

    private boolean isTypeCompatible(Type graphqlType, Class<?> javaType) {
        return isTypeCompatible(graphqlType, javaType, null);
    }

    private boolean isTypeCompatible(Type graphqlType, Class<?> javaType, AnnotatedType javaAnnotatedType) {
        if (graphqlType instanceof TypeName) {
            String typeName = typeToString(graphqlType);
            if (scalarTypeMap.containsKey(typeName)) {
                return scalarTypeMap.getOrDefault(typeName, Collections.emptySet()).contains(javaType);
            } else if (objectTypeMap.containsKey(typeName)) {
                return objectTypeMap.get(typeName) == javaType;
            } else if (inputObjectTypeMap.containsKey(typeName)) {
                return inputObjectTypeMap.get(typeName) == javaType;
            } else if (enumTypeMap.containsKey(typeName)) {
                return enumTypeMap.get(typeName) == javaType;
            } else if (interfaceTypeMap.containsKey(typeName)) {
                return interfaceTypeMap.get(typeName) == javaType;
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

    private String typeToString(Type graphqlType) {
        if (graphqlType instanceof TypeName) {
            return ((TypeName)graphqlType).getName();
        } else if (graphqlType instanceof ListType) {
            return String.format("[%s]", typeToString(((ListType)graphqlType).getType()));
        } else {
            throw new RuntimeException("Unknown type");
        }
    }

    private Constructor<?> findInputTypeConstructor(Class<?> inputType) {
        try {
            return inputType.getConstructor(Map.class);
        } catch (NoSuchMethodException e) {
            error("InputType %s doesn't have a Map<String,Object> constructor", inputType.getName());
            return null;
        }
    }

    private DataFetcher buildDataFetcherFromMethod(Method method, List<InputValueDefinition> fieldParams) {
        return env -> {
            Object source = env.getSource();

            // TODO: This logic could be done before running the datafetcher. Also check for default constructor.
            if (source == null && !Modifier.isStatic(method.getModifiers())) {
                try {
                    source = method.getDeclaringClass().newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(String.format("Unable to create instance of class %s",
                            method.getDeclaringClass().getSimpleName()), e);
                }
            }

            List<Object> parameters = new ArrayList<>();
            parameters.add(env);
            try {
                for (InputValueDefinition fieldParam : fieldParams) {
                    Object paramValue = env.getArgument(fieldParam.getName());
                    if (fieldParam.getType() instanceof TypeName) {
                        String fieldName = typeToString(fieldParam.getType());

                        Class<?> inputType = inputObjectTypeMap.get(fieldName);
                        if (inputType != null) {
                            Constructor<?> constructor = findInputTypeConstructor(inputType);
                            Object parameter = constructor.newInstance((Map) paramValue);
                            parameters.add(parameter);
                            continue;
                        }

                        @SuppressWarnings("unchecked")
                        Class<? extends Enum> enumType = (Class<? extends Enum>) enumTypeMap.get(fieldName);
                        if (enumType != null) {
                            Enum<?> parameter = Enum.valueOf(enumType, (String)paramValue);
                            parameters.add(parameter);
                            continue;
                        }
                    }
                    parameters.add(paramValue);
                }
                return method.invoke(source, parameters.toArray());
            } catch (Exception e) {
                throw new RuntimeException("Error invoking data fetcher", e);
            }
        };
    }

    private DataFetcher buildDataFetcherFromGetter(Method getter) {
        return env -> {
            Object source = env.getSource();
            // TODO: This logic could be done before running the datafetcher. Also check for default constructor.
            // TODO: This logic is repeated in buildDataFetcherFromMethod
            if (source == null && !Modifier.isStatic(getter.getModifiers())) {
                try {
                    source = getter.getDeclaringClass().newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(String.format("Unable to create instance of class %s",
                            getter.getDeclaringClass().getSimpleName()), e);
                }
            }
            try {
                return getter.invoke(source);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking data fetcher: " + e.toString(), e);
            }
        };
    }

    private TypeResolver buildTypeResolver(String interfaceName) {
        Map<Class<?>, String> implementingClasses = interfacesImplemented.keySet().stream()
                .filter(c -> interfacesImplemented.get(c).contains(interfaceName))
                .collect(Collectors.toMap(objectTypeMap::get, Function.identity()));

        return env -> {
            Object javaObject = env.getObject();
            String className = implementingClasses.get(javaObject.getClass());
            return (GraphQLObjectType) env.getSchema().getType(className);
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
