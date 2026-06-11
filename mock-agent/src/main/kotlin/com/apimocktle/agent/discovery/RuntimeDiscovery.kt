package com.apimocktle.agent

/**
 * 运行时类发现 —— 扫描已加载的类，发现可拦截的 FeignClient 和 MyBatis Mapper
 */
object RuntimeDiscovery {

    /**
     * 发现目标进程中已加载的可拦截类
     */
    fun discover(instrumentation: java.lang.instrument.Instrumentation): DiscoverResult {
        val loadedClasses: Array<Class<*>> = instrumentation.allLoadedClasses ?: emptyArray()
        val feignClients = mutableListOf<DiscoverClassInfo>()
        val mappers = mutableListOf<DiscoverClassInfo>()

        for (clazz in loadedClasses) {
            try {
                val resolvedClass = try {
                    Class.forName(clazz.name, false, clazz.classLoader ?: ClassLoader.getSystemClassLoader())
                } catch (_: ClassNotFoundException) {
                    continue
                } catch (_: NoClassDefFoundError) {
                    continue
                }

                // 检测 FeignClient 接口
                if (isFeignClient(resolvedClass)) {
                    feignClients.add(buildClassInfo(resolvedClass))
                }

                // 检测 MyBatis Mapper 接口
                if (isMyBatisMapper(resolvedClass)) {
                    mappers.add(buildClassInfo(resolvedClass))
                }
            } catch (_: Throwable) {
                // 忽略无法解析的类
            }
        }

        return DiscoverResult(
            feignClients = feignClients.sortedBy { it.className },
            mappers = mappers.sortedBy { it.className },
        )
    }

    /**
     * 检测是否为 FeignClient 接口
     * 条件：接口上有 @FeignClient 注解
     */
    private fun isFeignClient(clazz: Class<*>): Boolean {
        if (!clazz.isInterface) return false
        return clazz.annotations.any { annotation ->
            annotation.annotationClass.qualifiedName == "org.springframework.cloud.openfeign.FeignClient"
        }
    }

    /**
     * 检测是否为 MyBatis Mapper 接口
     * 条件：接口被 MapperProxy 代理，或有 @Mapper 注解
     */
    private fun isMyBatisMapper(clazz: Class<*>): Boolean {
        if (!clazz.isInterface) return false
        // 检查 @Mapper 注解
        if (clazz.annotations.any { it.annotationClass.simpleName == "Mapper" }) return true
        // 检查是否在 MyBatis 的 SqlSession 管理下（通过接口名模式推断）
        // 常见模式：包名含 mapper/dao/repository，且是接口
        val pkg = clazz.packageName.lowercase()
        return (pkg.contains("mapper") || pkg.contains("dao") || pkg.contains("repository"))
                && clazz.methods.isNotEmpty()
    }

    /**
     * 构建类信息（方法列表）
     */
    private fun buildClassInfo(clazz: Class<*>): DiscoverClassInfo {
        val methods = clazz.methods
            .filter { it.declaringClass != Any::class.java }
            .map { method ->
                val paramTypes = method.parameterTypes.map { it.name }
                val returnType = method.genericReturnType.typeName
                val params = method.parameterTypes.joinToString(", ") { it.simpleName }
                DiscoverMethodInfo(
                    name = method.name,
                    paramTypes = paramTypes,
                    returnType = returnType,
                    displayName = "${method.name}($params) → ${method.returnType.simpleName}",
                )
            }

        return DiscoverClassInfo(
            className = clazz.name,
            displayName = clazz.simpleName,
            methods = methods,
        )
    }
}
