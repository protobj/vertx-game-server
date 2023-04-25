package io.protobj.services.methods;

import io.protobj.services.api.Message;
import io.scalecube.services.annotations.*;
import io.scalecube.services.api.Qualifier;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.auth.Secured;
import io.scalecube.services.methods.MethodInfo;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.scalecube.services.CommunicationMode.*;

public class Reflect {

    private Reflect() {
        // Do not instantiate
    }

    /**
     * extract parameterized return value of a method.
     *
     * @param method to extract type from.
     * @return the generic type of the return value or object.
     */
    public static Type parameterizedReturnType(Method method) {
        Type type = method.getGenericReturnType();
        if (type instanceof ParameterizedType) {
            return ((ParameterizedType) type).getActualTypeArguments()[0];
        } else {
            return Object.class;
        }
    }

    /**
     * Util function to check if return type of method is ServiceMessage.
     *
     * @param method method to inspect
     * @return true if return type of method is ServiceMessage, otherwise false
     */
    public static boolean isReturnTypeMessage(Method method) {
        Type type = method.getGenericReturnType();

        if (type instanceof ParameterizedType) {
            Type actualReturnType = ((ParameterizedType) type).getActualTypeArguments()[0];

            return Message.class.equals(actualReturnType);
        }

        return false;
    }

    /**
     * Util function returns the the Type of method parameter [0] or Void.Type in case 0 parameters.
     * in case the method is annotated with @RequestType this type will always be chosen. if the
     * parameter is generic eg. &lt;String&gt; the actual type will be used. in case there is no
     * annotation and the type is not generic then return the actual type. in case method accepts
     * service message and no RequestType annotation is present then return Object.class
     *
     * @param method in inspection.
     * @return type of parameter [0] or void
     */
    public static Class<?> requestType(Method method) {
        if (method.getParameterTypes().length > 0) {
            if (method.getGenericParameterTypes()[0] instanceof ParameterizedType) {
                try {
                    return Class.forName(parameterizedRequestType(method).getTypeName());
                } catch (ClassNotFoundException e) {
                    return Object.class;
                }
            } else if (ServiceMessage.class.equals(method.getParameterTypes()[0])) {
                return Object.class;
            } else {
                return method.getParameterTypes()[0];
            }
        } else {
            return Void.TYPE;
        }
    }

    /**
     * Util function to check if the first parameter of method is ServiceMessage.
     *
     * @param method method to inspect
     * @return true if the first parameter of method is ServiceMessage, otherwise false
     */
    public static boolean isRequestTypeServiceMessage(Method method) {
        Type[] parameterTypes = method.getGenericParameterTypes();

        if (parameterTypes.length < 1) {
            return false;
        }

        if (parameterTypes[0] instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) parameterTypes[0];
            return ServiceMessage.class.equals(parameterizedType.getActualTypeArguments()[0]);
        }

        return ServiceMessage.class.equals(parameterTypes[0]);
    }

    /**
     * Util function that returns the parameterizedType of a given object.
     *
     * @param object to inspect
     * @return the parameterized Type of a given object or Object class if unknown.
     */
    @SuppressWarnings("unused")
    public static Type parameterizedType(Object object) {
        if (object != null) {
            Type type = object.getClass().getGenericSuperclass();
            if (type instanceof ParameterizedType) {
                return ((ParameterizedType) type).getActualTypeArguments()[0];
            }
        }
        return Object.class;
    }

    /**
     * Parse <code>serviceInterface</code> class and puts available methods annotated by {@link
     * ServiceMethod} annotation to {@link Method} -> {@link MethodInfo} mapping.
     *
     * @param serviceInterface - service interface to be parsed.
     * @return - mapping form available service methods of the <code>serviceInterface</code> to their
     * descriptions
     */
    public static Map<Method, MethodInfo> methodsInfo(Class<?> serviceInterface) {
        return Collections.unmodifiableMap(
                serviceMethods(serviceInterface).values().stream()
                        .collect(
                                Collectors.toMap(
                                        Function.identity(),
                                        method ->
                                                new MethodInfo(
                                                        serviceName(serviceInterface),
                                                        methodName(method),
                                                        parameterizedReturnType(method),
                                                        isReturnTypeServiceMessage(method),
                                                        communicationMode(method),
                                                        method.getParameterCount(),
                                                        requestType(method),
                                                        isRequestTypeServiceMessage(method),
                                                        isSecured(method)))));
    }

    /**
     * Util function that returns the parameterized of the request Type of a given object.
     *
     * @return the parameterized Type of a given object or Object class if unknown.
     */
    public static Type parameterizedRequestType(Method method) {
        if (method != null && method.getGenericParameterTypes().length > 0) {
            Type type = method.getGenericParameterTypes()[0];
            if (type instanceof ParameterizedType) {
                return ((ParameterizedType) type).getActualTypeArguments()[0];
            }
        }

        return Object.class;
    }

    /**
     * Util function to extract service name from service api.
     *
     * @param serviceInterface with @Service annotation.
     * @return service name.
     */
    public static String serviceName(Class<?> serviceInterface) {
        // Service name
        Service serviceAnnotation = serviceInterface.getAnnotation(Service.class);
        if (serviceAnnotation == null) {
            throw new IllegalArgumentException(
                    String.format("Not a service interface: %s", serviceInterface));
        }
        return serviceAnnotation.value().length() > 0
                ? serviceAnnotation.value()
                : serviceInterface.getName();
    }

    /**
     * Util function to extract service tags from service api.
     *
     * @param serviceInterface with @Service annotation.
     * @return service tags
     */
    public static Map<String, String> serviceTags(Class<?> serviceInterface) {
        return Reflect.transformArrayToMap(serviceInterface.getAnnotationsByType(Tag.class));
    }

    /**
     * Util function to extract service tags from service method api.
     *
     * @param serviceMethod with @ServiceMethod annotation.
     * @return service tags
     */
    public static Map<String, String> serviceMethodTags(Method serviceMethod) {
        return Reflect.transformArrayToMap(serviceMethod.getAnnotationsByType(Tag.class));
    }

    private static Map<String, String> transformArrayToMap(Tag[] array) {
        return array == null || array.length == 0
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(
                Arrays.stream(array).collect(Collectors.toMap(Tag::key, Tag::value)));
    }

    /**
     * Util function to get service Method map from service api.
     *
     * @param serviceInterface with @Service annotation.
     * @return service name.
     */
    public static Map<String, Method> serviceMethods(Class<?> serviceInterface) {
        Map<String, Method> methods =
                Arrays.stream(serviceInterface.getMethods())
                        .filter(method -> method.isAnnotationPresent(ServiceMethod.class))
                        .collect(Collectors.toMap(Reflect::methodName, Function.identity()));

        return Collections.unmodifiableMap(methods);
    }

    /**
     * Util function to get service interfaces collections from service instance.
     *
     * @param serviceObject with extends service interface with @Service annotation.
     * @return service interface class.
     */
    public static Stream<Class<?>> serviceInterfaces(Object serviceObject) {
        Class<?> current = serviceObject.getClass();
        Set<Class<?>> interfaces = new HashSet<>();
        while (current != Object.class) {
            interfaces.addAll(Arrays.asList(current.getInterfaces()));
            current = current.getSuperclass();
        }
        return interfaces.stream()
                .filter(interfaceClass -> interfaceClass.isAnnotationPresent(Service.class));
    }

    public static String methodName(Method method) {
        ServiceMethod methodAnnotation = method.getAnnotation(ServiceMethod.class);
        return methodAnnotation.value().length() > 0 ? methodAnnotation.value() : method.getName();
    }

    /**
     * Handy method to get qualifier String from service's interface and method.
     *
     * @param serviceInterface service interface to get qualifier for
     * @param method           service's method to get qualifier for
     * @return qualifier string
     */
    public static String qualifier(Class<?> serviceInterface, Method method) {
        return Qualifier.asString(Reflect.serviceName(serviceInterface), Reflect.methodName(method));
    }

    /**
     * Util function to perform basic validation of service message request.
     *
     * @param method service method.
     */
    public static void validateMethodOrThrow(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(Void.TYPE)) {
            return;
        } else if (!Publisher.class.isAssignableFrom(returnType)) {
            throw new UnsupportedOperationException("Service method return type can be Publisher only");
        }

        validateResponseType(method);
        validateRequestType(method);

        if (method.getParameterCount() > 1) {
            throw new UnsupportedOperationException(
                    "Service method can accept at maximum single parameter");
        }
    }

    private static void validateResponseType(Method method) {
        if (isReturnTypeServiceMessage(method)) {
            if (!method.isAnnotationPresent(ResponseType.class)) {
                throw new UnsupportedOperationException(
                        "Return type ServiceMessage cannot be used without @ResponseType method annotation");
            } else if (ServiceMessage.class.equals(method.getAnnotation(ResponseType.class).value())) {
                throw new UnsupportedOperationException(
                        "ServiceMessage is not allowed value for @ResponseType");
            }
        }
    }

    private static void validateRequestType(Method method) {
        if (isRequestTypeServiceMessage(method)) {
            if (!method.isAnnotationPresent(RequestType.class)) {
                throw new UnsupportedOperationException(
                        "Request type ServiceMessage cannot be used without @RequestType method annotation");
            } else if (ServiceMessage.class.equals(method.getAnnotation(RequestType.class).value())) {
                throw new UnsupportedOperationException(
                        "ServiceMessage is not allowed value for @RequestType");
            }
        }
    }

    /**
     * This method is used to get catual {@link CommunicationMode} os service method.
     *
     * <p>The following modes are supported:
     *
     * <ul>
     *   <li>{@link CommunicationMode#REQUEST_CHANNEL} - service has at least one parameter,and the
     *       first parameter is either of type return type {@link Flux} or {@link Publisher};
     *   <li>{@link CommunicationMode#REQUEST_STREAM} - service's return type is {@link Flux}, and
     *       parameter is not {@link Flux};
     *   <li>{@link CommunicationMode#REQUEST_RESPONSE} - service's return type is Mono;
     *   <li>{@link CommunicationMode#FIRE_AND_FORGET} - service returns void;
     * </ul>
     *
     * @param method - Service method to be analyzed.
     * @return - {@link CommunicationMode} of service method. If method does not correspond to any of
     * supported modes, throws {@link IllegalArgumentException}
     */
    public static CommunicationMode communicationMode(Method method) {
        Class<?> returnType = method.getReturnType();
        if (isRequestChannel(method)) {
            return REQUEST_CHANNEL;
        } else if (returnType.isAssignableFrom(Flux.class)) {
            return REQUEST_STREAM;
        } else if (returnType.isAssignableFrom(Mono.class)) {
            return REQUEST_RESPONSE;
        } else if (returnType.isAssignableFrom(Void.TYPE)) {
            return FIRE_AND_FORGET;
        } else {
            throw new IllegalArgumentException(
                    "Service method is not supported (check return type or parameter type): " + method);
        }
    }

    private static boolean isRequestChannel(Method method) {
        Class<?>[] reqTypes = method.getParameterTypes();
        return reqTypes.length > 0
                && (Flux.class.isAssignableFrom(reqTypes[0])
                || Publisher.class.isAssignableFrom(reqTypes[0]));
    }

    public static boolean isService(Class<?> type) {
        return type.isAnnotationPresent(Service.class);
    }

    public static boolean isSecured(Method method) {
        return method.isAnnotationPresent(Secured.class)
                || method.getDeclaringClass().isAnnotationPresent(Secured.class);
    }
}
