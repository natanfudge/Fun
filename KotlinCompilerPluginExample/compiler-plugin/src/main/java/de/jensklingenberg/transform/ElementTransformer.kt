package de.jensklingenberg.transform

import de.jensklingenberg.DebugLogger
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.FqName

internal class ElementTransformer(
    private val pluginContext: IrPluginContext,
    private val debugLogger: DebugLogger
) : IrElementTransformerVoidWithContext() {

    private val barClassFqName = FqName("sample.Bar")

    override fun visitClassNew(declaration: IrClass): IrStatement {
        debugLogger.log("Visiting class: ${declaration.name.asString()}")
//        debugLogger.log("BCS: (${declaration.name.asString()})")
        debugLogger.log("Before checking supertypes: (${declaration.name.asString()})")
        val barSuperType = declaration.superTypes.find { it.classFqName == barClassFqName }
//        val barSuperType = null
        debugLogger.log("After checking supertypes: (${declaration.name.asString()}")
        if (barSuperType != null) {
            debugLogger.log("Found supertype 'Bar' for class: ${declaration.name.asString()}")
            declaration.declarations.forEach { member ->
                if (member is IrConstructor) {
                    debugLogger.log("Checking constructor in :${declaration.name.asString()}")
                    member.body?.statements?.forEach { stmt ->
                        if (stmt is IrDelegatingConstructorCall) {
                            debugLogger.log("Found delegating constructor call in :${declaration.name.asString()}")
                            // Check if it's a call to the superclass (Bar) constructor
                            val isSuperCall = stmt.symbol.owner.parentAsClass.symbol == barSuperType.classOrNull
                            debugLogger.log("Is super call to Bar? :$isSuperCall")
                            if (isSuperCall) {
                                // Check if the constructor call already has arguments
//                                val hasArgs = stmt.valueArgumentsCount > 0
                                val hasArgs = stmt.valueArguments.filterNotNull().isNotEmpty()
                                debugLogger.log("Does super call have arguments? :$hasArgs")
                                if (!hasArgs) {
                                    val className = declaration.name.asString()
                                    debugLogger.log("Injecting :'$className' argument for ${declaration.name.asString()}")
                                    val classNameArgument = IrConstImpl.string(
                                        stmt.startOffset,
                                        stmt.endOffset,
                                        pluginContext.irBuiltIns.stringType,
                                        className
                                    )
                                    stmt.putValueArgument(0, classNameArgument)
                                    debugLogger.log("Injected :'$className' into super constructor call for ${declaration.name.asString()}")
                                } else {
                                    debugLogger.log("Super call does have arguments: ${stmt.valueArgumentsCount}. They are: ${stmt.valueArguments}")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            debugLogger.log("Can't find the supertype for class: ${declaration.name.asString()}, we have: ${declaration.superTypes.map { it.classFqName }}")
        }
        return super.visitClassNew(declaration)
    }

    override fun visitValueParameterNew(declaration: IrValueParameter): IrStatement {
        debugLogger.log("Visiting visitValueParameterNew: ${declaration.name.asString()}")

        declaration.transform(CreateFuncTransformer(pluginContext,debugLogger), null)
        return super.visitValueParameterNew(declaration)
    }

    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        debugLogger.log("Visiting visitPropertyNew: ${declaration.name.asString()}")

        declaration.transform(CreateFuncTransformer(pluginContext, debugLogger), null)
        return super.visitPropertyNew(declaration)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        debugLogger.log("Visiting visitCall")

        expression.transform(CreateFuncTransformer(pluginContext, debugLogger), null)
        return super.visitCall(expression)
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        debugLogger.log("Visiting visitVariable")

        declaration.transform(CreateFuncTransformer(pluginContext, debugLogger), null)
        return super.visitVariable(declaration)
    }


    override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
        debugLogger.log("Visiting visitFunctionExpression")
        expression.transform(CreateFuncTransformer(pluginContext, debugLogger), null)
        return super.visitFunctionExpression(expression)
    }
}