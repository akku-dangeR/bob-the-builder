package org.eclipselabs.bobthebuilder.composer;

import javax.inject.Inject;

import org.apache.commons.lang.Validate;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipselabs.bobthebuilder.DialogContent;
import org.eclipselabs.bobthebuilder.analyzer.WithMethodPredicate;
import org.eclipselabs.bobthebuilder.mapper.eclipse.FlattenedICompilationUnit;
import org.eclipselabs.bobthebuilder.model.Field;
import org.eclipselabs.bobthebuilder.model.JavaClassFile;

public class Composer {

  private final ConstructorComposer constructorComposer;

  private final BuilderComposer builderComposer;

  private final WithMethodPredicate withMethodPredicate;

  @Inject
  public Composer(ConstructorComposer constructorComposer, BuilderComposer builderComposer,
      WithMethodPredicate withMethodPredicate) {
    this.constructorComposer = constructorComposer;
    this.builderComposer = builderComposer;
    this.withMethodPredicate = withMethodPredicate;
  }

  public void compose(ComposerRequest request,
      DialogContent dialogRequest,
      FlattenedICompilationUnit flattenedICompilationUnit, 
      JavaClassFile javaClassFile) throws JavaModelException {
    ICompilationUnit compilationUnit = flattenedICompilationUnit.getCompilationUnit();
    compilationUnit.becomeWorkingCopy(null);
    IType type = flattenedICompilationUnit.getMainType();
    if (request.isCreateConstructorWithBuilder()) {
      String constructorWithBuilderBuilder =
          constructorComposer.composeFromScratch(request, type.getElementName());
      type.createMethod(
        constructorWithBuilderBuilder, flattenedICompilationUnit.getBuilderType(), false, null);
    }
    if (flattenedICompilationUnit.getConstructorWithBuilder() != null &&
          (!request.getMissingAssignmentsInConstructor().isEmpty() ||
              !request.getExtraFieldsInBuilder().isEmpty())) {
      String sourceLines =
          constructorComposer.composeFromExisting(
            request, javaClassFile.getMainType().getConstructorWithBuilder());
      IMethod originalConstructorWithBuilder =
          flattenedICompilationUnit.getConstructorWithBuilder();
      originalConstructorWithBuilder.delete(false, null);
      type.createMethod(sourceLines, null, false, null);
    }
    IType builder;
    if (flattenedICompilationUnit.getBuilderType() == null) {
      String builderSkeleton = builderComposer.composeSkeleton();
      type.createType(builderSkeleton, null, false, null);
      IType[] types = type.getTypes();
      Validate.notEmpty(types, "types may not be empty");
      builder = types[0];
    }
    else {
      builder = flattenedICompilationUnit.getBuilderType();
    }
    for (Field each : request.getMissingFieldsInBuilder()) {
      String composeFieldInBuilder = builderComposer.composeFieldDeclaration(each);
      builder.createField(composeFieldInBuilder, null, true, null);
    }
    for (Field each : request.getExtraFieldsInBuilder()) {
      for (IField eachBuilderField : builder.getFields()) {
        if (eachBuilderField.getElementName().equals(each.getName())) {
          eachBuilderField.delete(false, null);
        }
      }
      for (IMethod eachBuilderMethod : builder.getMethods()) {
        if (withMethodPredicate.match(each, eachBuilderMethod)) {
          eachBuilderMethod.delete(false, null);
        }
      }
    }
    for (Field each : request.getMissingWithMethodsInBuilder()) {
      String composeWithMethod = builderComposer.composeWithMethod(each);
      builder.createMethod(composeWithMethod, null, false, null);
    }
    if (request.isCreateBuildMethodInBuilder()) {
      String composeBuilderMethod = 
        builderComposer.composeBuilderMethod(
          javaClassFile.getMainType(), request.isCreateValidateMethodInBuilder());
      builder.createMethod(composeBuilderMethod, null, false, null);
    }
    if (request.isCreateValidateMethodInBuilder()) {
      String composeValidateMethod = builderComposer.composeValidateMethodFromScratch(
        request.getMissingFieldValidationsInBuild(),
        request.getValidationFramework());
      builder.createMethod(composeValidateMethod, null, false, null);
    }
    else if (!request.getMissingFieldValidationsInBuild().isEmpty()) {
      String newLines = builderComposer.composeValidateMethodFromExisting(
        request, javaClassFile.getMainType().getBuilderType().getValidateMethod());
      IMethod originalValidateMethod = flattenedICompilationUnit.getValidateMethod();
      originalValidateMethod.delete(false, null);
      builder.createMethod(newLines, null, false, null);
    }
    if (request.isCreateValidateMethodInBuilder()
          || !request.getMissingFieldValidationsInBuild().isEmpty()) {
      compilationUnit.createImport(
          request.getValidationFramework().getFullClassName(), null, null);
    }
    compilationUnit.commitWorkingCopy(true, null);
  }
}