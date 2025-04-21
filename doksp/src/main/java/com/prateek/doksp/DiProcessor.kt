package com.prateek.doksp

import androidx.annotation.Keep
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import com.prateek.doksp.inject_annotations.Inject
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass


class DiProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) :
    SymbolProcessor {

    val ANY = ClassName("kotlin", "Any").copy(nullable = true)

    //If outType is Any?, this returns *, which is shorthand for out Any
    val STAR = WildcardTypeName.producerOf(ANY)

    /**
     * Keep *pure metadata* instead of raw KS* symbols, otherwise you risk the
     * dreaded *KaInvalidLifetimeOwnerAccessException* when PSI changes between
     * rounds.
     */
    private data class InjectInfo(
        val className: ClassName,
        val paramTypes: List<TypeName>
    )

    // Accumulate validated @Inject constructors across rounds
    private val collectedInjects = mutableListOf<InjectInfo>()

    override fun process(resolver: Resolver): List<KSAnnotated> {

        // 1. Collect @Inject constructors
        //Function Declaration bcz we are putting it on the constructor method
        val injects = resolver.getSymbolsWithAnnotation(Inject::class.qualifiedName!!)

        val injectClasses = injects.filter {
            it.validate() && it is KSFunctionDeclaration
        }.map {
            it as KSFunctionDeclaration
        }

        injectClasses.forEach { injClass ->
            generateFactory(injClass)

            val klass = (injClass.parentDeclaration as KSClassDeclaration).toClassName()
            val deps = injClass.parameters.map { it.type.toTypeName() }
            collectedInjects += InjectInfo(klass, deps)
        }

        return injectClasses.filter { !it.validate() }.toList()

    }

    /**
     * Called exactly once **after the last processing round** in KSP ≥ 1.9.x.
     * Perfect place to emit aggregated files such as the Component.
     */
    override fun finish() {
        super.finish()
        generateComponent(collectedInjects)
    }

    /*
    This will create these kind of Factories for this Constructor:-
    class ViewModel
    @Inject constructor(
    private val repo: Repository1,
    private val repository2: Repository2)


    Generated:-
    package com.prateek.doingksp

    public class ViewModelFactory {
        public fun create(p0: Repository1, p1: Repository2): ViewModel = ViewModel(p0, p1)
    }
     */

    private fun generateFactory(injectClass: KSFunctionDeclaration) {

        val className = injectClass.parentDeclaration!!.simpleName.asString()
        val packageName = injectClass.parentDeclaration!!.packageName.asString()

        //The doubt is how will it deal with Custom Types?
        //Like something which is not Int or String
        val dependencies = injectClass.parameters.map {
            it.type.toTypeName()
        }
        val factoryName = "${className}Factory"


        val fileSpec = FileSpec.builder(packageName, factoryName)
            .addType(
                TypeSpec.classBuilder(factoryName)
                    .addFunction(
                        FunSpec.builder("create")
                            .returns(ClassName(packageName, className))
                            .addParameters(
                                dependencies.mapIndexed { index, t ->
                                    ParameterSpec.builder("p$index", t)
                                        .build()
                                }
                            )
                            .addStatement(
                                "return %T(${dependencies.indices.joinToString { "p$it" }})",
                                ClassName(packageName, className)
                            )
                            .build()
                    )
                    //To prevent pruning from build time
                    .addAnnotation(AnnotationSpec.builder(Keep::class).build())
                    .build()
            ).build()

        fileSpec.writeTo(codeGenerator, Dependencies(true))

    }

    private fun generateComponent(injectCtors: List<InjectInfo>) {
        val fileSpec = buildComponentSpec(injectCtors)

        val file = codeGenerator.createNewFile(
            Dependencies(aggregating = true),
            "com.di",
            "DIComponent"
        )
        file.bufferedWriter().use { writer ->
            fileSpec.writeTo(writer)
        }
    }

    private fun buildComponentSpec(injectCtors: List<InjectInfo>): FileSpec {
        val getFun = buildGetFun(injectCtors)

        //The Inline Public function to get dependency in the app
        val reifiedWrapper = FunSpec.builder("inject")
            .addTypeVariable(TypeVariableName("T").copy(reified = true))
            //.receiver(ClassName("tinydi.generated", "TinyDIComponent"))
            .addModifiers(KModifier.INLINE)
            .returns(TypeVariableName("T"))
            .addStatement("return getDependency(T::class) as T")
            .build()


        val runTimeBindingProperty =
            PropertySpec.builder(
                "runTimeBindings", MUTABLE_MAP
                    .parameterizedBy(
                        KClass::class.asClassName().parameterizedBy(STAR),
                        ClassName("kotlin", "Any")
                    ), KModifier.PUBLIC //Public bcz we are using inline to set this
            )
                .mutable(false)
                .initializer("mutableMapOf()")
                .build()

        //Bind function to add dependencies to the map we just created
        val type = TypeVariableName("T", ANY.copy(nullable = false)).copy(reified = true)
        val bindRuntimeFunction = FunSpec.builder("bind")
            .addTypeVariable(type)
            .addModifiers(KModifier.INLINE)
            .addParameter(ParameterSpec("instance", TypeVariableName("T")))
            .addStatement("runTimeBindings[T::class] = instance")
            .build()


        val componentType = TypeSpec.classBuilder("TinyDIComponent")
            .addFunction(getFun)
            .addFunction(reifiedWrapper)
            .addFunction(bindRuntimeFunction)
            .addProperty(runTimeBindingProperty)
            .build()

        return FileSpec.builder("tinydi.generated", "TinyDIComponent")
            .addType(componentType)
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildGetFun(injectCtors: List<InjectInfo>): FunSpec {
        val clazzParam =
            ParameterSpec.builder("clazz", KClass::class.asClassName().parameterizedBy(STAR))
                .build()

        val funName = "getDependency"

        val funBuilder = FunSpec.builder(funName)
            .addTypeVariable(TypeVariableName("T"))
            //.receiver(ClassName("tinydi.generated", "TinyDIComponent"))
            .addParameter(clazzParam)
            .returns(TypeVariableName("T"))
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "UNCHECKED_CAST")
                    .build()
            )

        funBuilder.beginControlFlow("return runTimeBindings[clazz] as? T ?: when (clazz)")
        injectCtors.forEach { ctor ->
            val factoryName =
                ClassName(ctor.className.packageName, "${ctor.className.simpleName}Factory")
            val depsCalls = ctor.paramTypes.map {
                "$funName(${it}::class)" // This will be gerDependency(NetworkClient::class) for each param
            }

            //%T in KotlinPoet Statement takes ClassName and it imports package for it and basically takes the class
            val creation = "%T().create(${depsCalls.joinToString()})"
            funBuilder.addStatement("%T::class -> $creation as T", ctor.className, factoryName)
        }
        //funBuilder.addStatement("else -> error(\"No binding for $\{clazz\}\")")
        funBuilder.addStatement("else -> error(\"No Binding for \")")
        funBuilder.endControlFlow()

        return funBuilder.build()
    }


}