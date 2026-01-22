// Script to generate JNI class/method/field cache
import * as fs from "fs";
import * as path from "path";
import { promisify } from "util";

const HEADER_DEFINE = "QJS_KT_JNI_GLOBALS_GENERATED_H";

const WRITE_PATH = "quickjs/native/jni";
const FILE_BASE_NAME = "jni_globals_generated";

const JNI_REFS = [
  {
    className: "kotlin/UByteArray",
    methods: [{ name: "<init>", sign: "([B)V" }],
    fields: [{ name: "storage", type: "[B" }],
  },
  {
    className: "java/lang/Short",
    methods: [
      { name: "shortValue", sign: "()S" },
    ],
  },
  {
    className: "java/lang/Byte",
    methods: [
      { name: "byteValue", sign: "()B" },
    ],
  },
  {
    className: "java/lang/Integer",
    methods: [
      { name: "valueOf", sign: "(I)Ljava/lang/Integer;", isStatic: true },
      { name: "intValue", sign: "()I" },
    ],
  },
  {
    className: "java/lang/Long",
    methods: [
      { name: "valueOf", sign: "(J)Ljava/lang/Long;", isStatic: true },
      { name: "longValue", sign: "()J" },
    ],
  },
  {
    className: "java/lang/Float",
    methods: [{ name: "floatValue", sign: "()F" }],
  },
  {
    className: "java/lang/Double",
    methods: [
      { name: "valueOf", sign: "(D)Ljava/lang/Double;", isStatic: true },
      { name: "doubleValue", sign: "()D" },
    ],
    fields: [{ name: "NaN", type: "D", isStatic: true }],
  },
  {
    className: "java/lang/Boolean",
    methods: [
      { name: "valueOf", sign: "(Z)Ljava/lang/Boolean;", isStatic: true },
      { name: "booleanValue", sign: "()Z" },
    ],
  },
  {
    className: "java/lang/String",
    methods: [],
  },
  {
    className: "java/lang/Object",
    methods: [{ name: "toString", sign: "()Ljava/lang/String;" }],
  },
  {
    className: "java/lang/System",
    methods: [
      {
        name: "identityHashCode",
        sign: "(Ljava/lang/Object;)I",
        isStatic: true,
      },
    ],
  },
  {
    className: "java/lang/Class",
    methods: [
      { name: "getName", sign: "()Ljava/lang/String;" },
      { name: "isArray", sign: "()Z" },
    ],
  },
  {
    className: "java/lang/Throwable",
    methods: [
      { name: "getMessage", sign: "()Ljava/lang/String;" },
      { name: "getStackTrace", sign: "()[Ljava/lang/StackTraceElement;" },
    ],
  },
  {
    className: "java/util/Set",
    methods: [
      { name: "iterator", sign: "()Ljava/util/Iterator;" },
      { name: "add", sign: "(Ljava/lang/Object;)Z" },
      { name: "contains", sign: "(Ljava/lang/Object;)Z" },
      { name: "isEmpty", sign: "()Z" },
    ],
  },
  {
    className: "java/util/Iterator",
    methods: [
      { name: "hasNext", sign: "()Z" },
      { name: "next", sign: "()Ljava/lang/Object;" },
    ],
  },
  {
    className: "java/util/List",
    methods: [
      { name: "size", sign: "()I" },
      { name: "get", sign: "(I)Ljava/lang/Object;" },
      { name: "add", sign: "(Ljava/lang/Object;)Z" }
    ],
  },
  {
    className: "java/util/ArrayList",
    methods: [
      { name: "<init>", sign: "()V" },
      { name: "<init>", alias: "init_with_capacity", sign: "(I)V" },
    ],
  },
  {
    className: "java/util/Map",
    methods: [{ name: "entrySet", sign: "()Ljava/util/Set;" }],
  },
  {
    className: "java/util/Map$Entry",
    methods: [
      { name: "getKey", sign: "()Ljava/lang/Object;" },
      { name: "getValue", sign: "()Ljava/lang/Object;" },
    ],
  },
  {
    className: "java/util/HashSet",
    methods: [{ name: "<init>", sign: "()V" }],
  },
  {
    className: "java/util/LinkedHashMap",
    methods: [
      { name: "<init>", sign: "()V" },
      {
        name: "put",
        sign: "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
      },
    ],
  },
  {
    className: "java/util/LinkedHashSet",
    methods: [
      { name: "<init>", sign: "()V" },
      {
        name: "add",
        sign: "(Ljava/lang/Object;)Z",
      },
    ],
  },
  {
    className: "com/dokar/quickjs/QuickJsException",
    methods: [{ name: "<init>", sign: "(Ljava/lang/String;)V" }],
  },
  {
    className: "com/dokar/quickjs/QuickJs",
    methods: [
      {
        name: "onCallGetter",
        sign: "(JLjava/lang/String;)Ljava/lang/Object;",
      },
      {
        name: "onCallSetter",
        sign: "(JLjava/lang/String;Ljava/lang/Object;)V",
      },
      {
        name: "onCallFunction",
        sign: "(JLjava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
      },
      {
        name: "setEvalException",
        sign: "(Ljava/lang/Throwable;)V",
      },
      {
        name: "setUnhandledPromiseRejection",
        sign: "(Ljava/lang/Object;)V",
      },
      {
        name: "clearHandledPromiseRejection",
        sign: "()V",
      },
    ],
  },
  {
    className: "com/dokar/quickjs/MemoryUsage",
    methods: [{ name: "<init>", sign: "(JJJJJJJJJJJJJJJJJJJJJJJJJJ)V" }],
  },
  {
    className: "com/dokar/quickjs/binding/JsProperty",
    fields: [
      { name: "name", type: "Ljava/lang/String;" },
      { name: "configurable", type: "Z" },
      { name: "writable", type: "Z" },
      { name: "enumerable", type: "Z" },
    ],
  },
  {
    className: "com/dokar/quickjs/binding/JsFunction",
    fields: [
      { name: "name", type: "Ljava/lang/String;" },
      { name: "isAsync", type: "Z" },
    ],
  },
  {
    className: "com/dokar/quickjs/binding/JsObject",
    methods: [{ name: "<init>", sign: "(Ljava/util/Map;)V" }],
  },
];

/**
 * @param {string} text Camel case class name or method name or field name.
 */
function camelCaseSnackCase(text) {
  const parts = text.split("/");
  // Ignore package name
  let result = parts[parts.length - 1];

  // Remove $
  result = result.replace(/[\$<>]/g, "");

  // Insert _ between words
  // Match '[A]bb[C]DD'
  result = result.replace(/(?<![A-Z])[A-Z](?![A-Z])/g, (match) => {
    return "_" + match.toLowerCase();
  });

  // Remove leading _
  if (result.startsWith("_")) {
    result = result.substring(1);
  }

  return result.toLowerCase();
}

/**
 * @param {string} jniName
 * @returns {string}
 */
function srcClassName(jniName) {
  return "cls_" + camelCaseSnackCase(jniName);
}

/**
 * @param {string} jniClassName
 * @param {string} jniName
 * @param {string} alias Optional alias for the method
 * @returns {string}
 */
function srcMethodName(jniClassName, jniName, alias) {
  return (
    "method_" +
    camelCaseSnackCase(jniClassName) +
    "_" +
    camelCaseSnackCase(alias != null ? alias : jniName)
  );
}

/**
 * @param {string} jniClassName
 * @param {string} jniName
 * @returns {string}
 */
function srcFieldName(jniClassName, jniName) {
  return (
    "field_" +
    camelCaseSnackCase(jniClassName) +
    "_" +
    camelCaseSnackCase(jniName)
  );
}

/**
 * @returns {string}
 */
function generateHeader() {
  const classDefinitions = JNI_REFS.reduce((acc, item) => {
    const { className } = item;
    const name = srcClassName(className);
    return acc.concat([`jclass ${name}(JNIEnv *env);`]);
  }, []);

  const methodDefinitions = JNI_REFS.reduce((acc, item) => {
    const { className, methods } = item;
    if (methods == null) {
      return acc;
    }
    return acc.concat(
      methods.map((method) => {
        const name = srcMethodName(className, method.name, method.alias);
        return `jmethodID ${name}(JNIEnv *env);`;
      })
    );
  }, []);

  const fieldDefinitions = JNI_REFS.reduce((acc, item) => {
    const { className, fields } = item;
    if (fields == null) {
      return acc;
    }
    return acc.concat(
      fields.map((field) => {
        const name = srcFieldName(className, field.name);
        return `jfieldID ${name}(JNIEnv *env);`;
      })
    );
  }, []);

  const code = `/// Generated header file
#ifndef ${HEADER_DEFINE}
#define ${HEADER_DEFINE}

#include <jni.h>

${classDefinitions.join("\n\n")}

${methodDefinitions.join("\n\n")}

${fieldDefinitions.join("\n\n")}

void clear_jni_refs_cache(JNIEnv *env);

#endif // ${HEADER_DEFINE}
`;

  return code;
}

/**
 * @returns {string}
 */
function generateSource() {
  const classDeclarations = JNI_REFS.reduce((acc, item) => {
    const { className } = item;
    const name = srcClassName(className);
    return acc.concat([`static jclass _${name} = NULL;`]);
  }, []);

  const classBodies = JNI_REFS.reduce((acc, item) => {
    const { className } = item;
    const name = srcClassName(className);
    return acc.concat([
      `jclass ${name}(JNIEnv *env) {
    if (_${name} == NULL) {
        jclass cls = (*env)->FindClass(env, "${className}");
        _${name} = (*env)->NewGlobalRef(env, cls);
    }
    return _${name};
}`,
    ]);
  }, []);

  const classClearStatements = JNI_REFS.reduce((acc, item) => {
    const { className } = item;
    const name = srcClassName(className);
    return acc.concat([
      `if (_${name} != NULL) {
        (*env)->DeleteGlobalRef(env, _${name});
    }`,
    ]);
  }, []);

  const methodDeclarations = JNI_REFS.reduce((acc, item) => {
    const { className, methods } = item;
    if (methods == null) {
      return acc;
    }
    return acc.concat(
      methods.map((method) => {
        const name = srcMethodName(className, method.name, method.alias);
        return `static jmethodID _${name} = NULL;`;
      })
    );
  }, []);

  const methodBodies = JNI_REFS.reduce((acc, item) => {
    const { className, methods } = item;
    if (methods == null) {
      return acc;
    }
    const classFunName = srcClassName(className);
    return acc.concat(
      methods.map((method) => {
        const name = srcMethodName(className, method.name, method.alias);
        const staticPart = method.isStatic === true ? "Static" : "";
        return `jmethodID ${name}(JNIEnv *env) {
    if (_${name} == NULL) {
        _${name} = (*env)->Get${staticPart}MethodID(env, ${classFunName}(env), "${method.name}", "${method.sign}");
    }
    return _${name};
}`;
      })
    );
  }, []);

  const fieldDeclarations = JNI_REFS.reduce((acc, item) => {
    const { className, fields } = item;
    if (fields == null) {
      return acc;
    }
    return acc.concat(
      fields.map((field) => {
        const name = srcFieldName(className, field.name);
        return `static jfieldID _${name} = NULL;`;
      })
    );
  }, []);

  const fieldBodies = JNI_REFS.reduce((acc, item) => {
    const { className, fields } = item;
    if (fields == null) {
      return acc;
    }
    const classFunName = srcClassName(className);
    return acc.concat(
      fields.map((field) => {
        const name = srcFieldName(className, field.name);
        const staticPart = field.isStatic === true ? "Static" : "";
        return `jfieldID ${name}(JNIEnv *env) {
    if (_${name} == NULL) {
        _${name} = (*env)->Get${staticPart}FieldID(env, ${classFunName}(env), "${field.name}", "${field.type}");
    }
    return _${name};
}`;
      })
    );
  }, []);

  const code = `/// Generated source file
#include "${FILE_BASE_NAME}.h"

// Cached classes
${classDeclarations.join("\n")}

// Cached methods
${methodDeclarations.join("\n")}

// Cached fields
${fieldDeclarations.join("\n")}

${classBodies.join("\n\n")}

${methodBodies.join("\n\n")}

${fieldBodies.join("\n\n")}

void clear_jni_refs_cache(JNIEnv *env) {
${classClearStatements.map((item) => "    " + item).join("\n")}

${classDeclarations
      .map((item) => item.replace("static jclass ", "    "))
      .join("\n")}

${methodDeclarations
      .map((item) => item.replace("static jmethodID ", "    "))
      .join("\n")}

${fieldDeclarations
      .map((item) => item.replace("static jfieldID ", "    "))
      .join("\n")}
}
`;

  return code;
}

const headerFile = path.join(process.cwd(), WRITE_PATH, FILE_BASE_NAME + ".h");
const sourceFile = path.join(process.cwd(), WRITE_PATH, FILE_BASE_NAME + ".c");

await promisify(fs.writeFile)(headerFile, generateHeader());
await promisify(fs.writeFile)(sourceFile, generateSource());
