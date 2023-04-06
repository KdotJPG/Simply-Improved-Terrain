package jpg.k.simplyimprovedterrain.util;

import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.objectweb.asm.Type;

public class ReflectionUtils {

    public static boolean isMethodOverridden(Class<?> baseClass, Class<?> instanceClass, String intermediaryMethodName, Class<?> returnType, Class<?>... parameterTypes) {

        StringBuilder descriptorBuilder = new StringBuilder();
        descriptorBuilder.append("(");
        for (Class<?> parameterType : parameterTypes) {
            descriptorBuilder.append(Type.getDescriptor(parameterType));
        }
        descriptorBuilder.append(")");
        descriptorBuilder.append(Type.getDescriptor(returnType));
        String descriptor = descriptorBuilder.toString();

        MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
        String mappedMethodName = resolver.mapMethodName(
                "intermediary", resolver.unmapClassName("intermediary", instanceClass.getName()),
                intermediaryMethodName, descriptor
        );

        Method method;
        try {
            method = instanceClass.getDeclaredMethod(mappedMethodName, parameterTypes);
        } catch (NoSuchMethodException e) {

            // Consider it not overridden if it can't even be found in the first place.
            return false;

        }

        return !baseClass.equals(method.getDeclaringClass());
    }

}
