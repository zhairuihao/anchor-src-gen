package software.sava.anchor;

import java.util.List;
import java.util.Map;

public sealed interface AnchorTypeContext permits AnchorDefinedTypeContext, AnchorReferenceTypeContext {

  AnchorType type();

  default boolean isFixedLength(final Map<String, AnchorNamedType> definedTypes) {
    return false;
  }

  default int serializedLength(final Map<String, AnchorNamedType> definedTypes) {
    throw throwInvalidDataType();
  }

  default int fixedSerializedLength(final Map<String, AnchorNamedType> definedTypes) {
    return serializedLength(definedTypes);
  }

  default void generateMemCompFilter(final GenSrcContext genSrcContext,
                                     final StringBuilder builder,
                                     final String varName,
                                     final String offsetVarName,
                                     final boolean optional) {
    throw throwInvalidDataType();
  }

  default void generateMemCompFilter(final GenSrcContext genSrcContext,
                                     final StringBuilder builder,
                                     final String varName,
                                     final String offsetVarName) {
    generateMemCompFilter(genSrcContext, builder, varName, offsetVarName, false);
  }

  default RuntimeException throwInvalidDataType() {
    throw AnchorPrimitive.throwInvalidDataType(this.getClass());
  }

  default int numElements() {
    throw throwInvalidDataType();
  }

  default AnchorTypeContext genericType() {
    throw throwInvalidDataType();
  }

  default String typeName() {
    throw throwInvalidDataType();
  }

  default String optionalTypeName() {
    return typeName();
  }

  default int depth() {
    throw throwInvalidDataType();
  }

  default List<AnchorNamedType> values() {
    throw throwInvalidDataType();
  }

  default String generateRecordField(final GenSrcContext genSrcContext, final AnchorNamedType varName, final boolean optional) {
    throw throwInvalidDataType();
  }

  default String generateStaticFactoryField(final GenSrcContext genSrcContext, final String varName, final boolean optional) {
    throw throwInvalidDataType();
  }

  default String generateNewInstanceField(final GenSrcContext genSrcContext,
                                          final String varName) {
    throw throwInvalidDataType();
  }

  default String generateRead(final GenSrcContext genSrcContext) {
    throw throwInvalidDataType();
  }

  default String generateRead(final GenSrcContext genSrcContext,
                              final String varName,
                              final boolean hasNext) {
    throw throwInvalidDataType();
  }

  default String generateWrite(final GenSrcContext genSrcContext,
                               final String varName,
                               final boolean hasNext) {
    throw throwInvalidDataType();
  }

  default String generateEnumRecord(final GenSrcContext genSrcContext,
                                    final String enumTypeName,
                                    final AnchorNamedType enumName,
                                    final int ordinal) {
    throw throwInvalidDataType();
  }

  default String generateLength(final String varName) {
    throw throwInvalidDataType();
  }

  default int generateIxSerialization(final GenSrcContext genSrcContext,
                                      final AnchorNamedType context,
                                      final StringBuilder paramsBuilder,
                                      final StringBuilder dataBuilder,
                                      final StringBuilder stringsBuilder,
                                      final StringBuilder dataLengthBuilder,
                                      final boolean hasNext) {
    throw throwInvalidDataType();
  }
}
