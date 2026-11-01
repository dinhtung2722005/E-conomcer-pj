package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.asm.*;

/** Checks compiled dependencies, including method bodies, generic signatures, annotations and lambdas. */
class ModuleArchitectureTests {
    private static final String ROOT="com.example.demo.";
    private static final Set<String> BUSINESS=Set.of("user","ecommerce","wallet");
    private static final Map<String,Set<String>> ALLOWED=Map.of(
            "user",Set.of("shared"),"ecommerce",Set.of("shared","user"),
            "wallet",Set.of("shared","user","ecommerce"),
            "application",Set.of("shared","user","ecommerce","wallet"),
            "observability",Set.of("shared"),"shared",Set.of());
    private static final Pattern SIGNATURE=Pattern.compile("L(com/example/demo/[A-Za-z0-9_/$]+)");
    private static final Pattern QUERY_ENTITY=Pattern.compile("(?i)\\b(?:from|join|update|into)\\s+`?([A-Za-z0-9_$]+)`?");

    @Test void modulesAccessOnlyAllowedPublicContractsAndHaveNoCycles()throws Exception{
        var dependencies=compiledDependencies();
        List<String> violations=new ArrayList<>();
        Map<String,Set<String>> graph=new TreeMap<>();
        dependencies.forEach((source,targets)->{
            for(String target:targets){
                if(!allowed(source,target))violations.add(source+" -> "+target);
                String from=module(source),to=module(target);
                if(!from.equals(to)&&ALLOWED.containsKey(from)&&ALLOWED.containsKey(to))graph.computeIfAbsent(from,k->new TreeSet<>()).add(to);
            }
        });
        assertTrue(violations.isEmpty(),()->"Forbidden implementation dependencies:\n"+String.join("\n",violations));
        for(String start:graph.keySet())assertAcyclic(start,graph,new LinkedHashSet<>(),new HashSet<>());
    }

    @Test void publicContractsDoNotExposePersistentEntitiesOrImplementationClasses()throws Exception{
        var dependencies=compiledDependencies();
        List<String> leaks=new ArrayList<>();
        dependencies.forEach((source,targets)->{
            if(BUSINESS.contains(module(source))&&source.startsWith(ROOT+module(source)+".api."))
                targets.stream().filter(t->BUSINESS.contains(module(t))&&!t.startsWith(ROOT+module(t)+".api."))
                        .forEach(t->leaks.add(source+" -> "+t));
        });
        assertTrue(leaks.isEmpty(),()->"API exposes implementation types:\n"+String.join("\n",leaks));
    }

    @Test void rulesDetectRepositoryAccessReverseDependenciesAndLeakedEntities(){
        assertFalse(allowed(ROOT+"wallet.service.Payments",ROOT+"ecommerce.repository.OrderRepository"));
        assertFalse(allowed(ROOT+"user.service.Registration",ROOT+"wallet.api.WalletAccountsApi"));
        assertFalse(allowed(ROOT+"ecommerce.service.Orders",ROOT+"application.OrderWorkflowApplicationService"));
        assertTrue(allowed(ROOT+"application.Payments",ROOT+"wallet.api.WalletPaymentsApi"));
        assertTrue(allowed(ROOT+"wallet.service.Payments",ROOT+"ecommerce.api.OrderSnapshot"));
    }

    @Test void queryInspectionDetectsHiddenEntityDependencies(){
        var collector=new Dependencies();
        collector.queries.add("select o.id from Order o where not exists (select r.id from WalletRefundCase r where r.orderId=o.id)");
        addQueryDependencies(collector,Map.of("walletrefundcase",ROOT+"wallet.entity.WalletRefundCase","order",ROOT+"ecommerce.entity.Order"));
        assertTrue(collector.targets.contains(ROOT+"wallet.entity.WalletRefundCase"));
        assertFalse(allowed(ROOT+"ecommerce.repository.OrderRepository",ROOT+"wallet.entity.WalletRefundCase"));
    }

    private static boolean allowed(String source,String target){
        if(!source.startsWith(ROOT)||!target.startsWith(ROOT))return true;
        String from=module(source),to=module(target);
        if(from.equals(to))return true;
        if(!ALLOWED.getOrDefault(from,Set.of()).contains(to))return false;
        return !BUSINESS.contains(to)||target.startsWith(ROOT+to+".api.");
    }
    private static String module(String name){return name.startsWith(ROOT)?name.substring(ROOT.length()).split("\\.")[0]:"external";}
    private static void assertAcyclic(String node,Map<String,Set<String>> graph,Set<String> visiting,Set<String> done){
        if(done.contains(node))return;
        assertTrue(visiting.add(node),()->"Cyclic module dependencies: "+visiting+" -> "+node);
        for(String next:graph.getOrDefault(node,Set.of()))assertAcyclic(next,graph,visiting,done);
        visiting.remove(node);done.add(node);
    }

    private static Map<String,Set<String>> compiledDependencies()throws Exception{
        Path classes=Path.of(DemoApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Map<String,Set<String>> result=new TreeMap<>();
        List<Dependencies> collectors=new ArrayList<>();
        try(var paths=Files.walk(classes.resolve("com/example/demo"))){
            for(Path file:paths.filter(p->p.toString().endsWith(".class")).toList()){
                var collector=new Dependencies();new ClassReader(Files.readAllBytes(file)).accept(collector,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                collectors.add(collector);
            }
        }
        Map<String,String> entities=new HashMap<>();
        for(var collector:collectors)if(collector.entity){
            entities.put(collector.name.substring(collector.name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT),collector.name);
            if(collector.entityName!=null)entities.put(collector.entityName.toLowerCase(Locale.ROOT),collector.name);
            if(collector.tableName!=null)entities.put(collector.tableName.toLowerCase(Locale.ROOT),collector.name);
        }
        for(var collector:collectors){addQueryDependencies(collector,entities);result.put(collector.name,collector.targets);}
        return result;
    }
    private static void addQueryDependencies(Dependencies collector,Map<String,String> entities){
        for(String query:collector.queries){var matcher=QUERY_ENTITY.matcher(query);while(matcher.find()){
            String target=entities.get(matcher.group(1).toLowerCase(Locale.ROOT));if(target!=null)collector.targets.add(target);
        }}
    }
    private static class Dependencies extends ClassVisitor {
        String name,entityName,tableName;boolean entity;final Set<String> targets=new TreeSet<>();
        final List<String> queries=new ArrayList<>();
        Dependencies(){super(Opcodes.ASM9);}
        void type(String value){if(value!=null){String target=value.replace('/','.');if(target.startsWith(ROOT))targets.add(target);}}
        void signature(String value){if(value!=null){var matcher=SIGNATURE.matcher(value);while(matcher.find())type(matcher.group(1));}}
        AnnotationVisitor annotation(String descriptor){signature(descriptor);return new AnnotationVisitor(Opcodes.ASM9){
            @Override public void visit(String name,Object value){
                constant(value);
                if(descriptor.equals("Lorg/springframework/data/jpa/repository/Query;")&&value instanceof String text)queries.add(text);
                if(descriptor.equals("Ljakarta/persistence/Entity;")&&name.equals("name")&&value instanceof String text&&!text.isBlank())entityName=text;
                if(descriptor.equals("Ljakarta/persistence/Table;")&&name.equals("name")&&value instanceof String text)tableName=text;
            }
            @Override public void visitEnum(String name,String descriptor,String value){signature(descriptor);}
            @Override public AnnotationVisitor visitAnnotation(String name,String descriptor){return annotation(descriptor);}
            @Override public AnnotationVisitor visitArray(String name){return this;}
        };}
        void constant(Object value){
            if(value instanceof Type t)signature(t.getDescriptor());
            if(value instanceof Handle h){type(h.getOwner());signature(h.getDesc());}
            if(value instanceof ConstantDynamic c){signature(c.getDescriptor());constant(c.getBootstrapMethod());for(int i=0;i<c.getBootstrapMethodArgumentCount();i++)constant(c.getBootstrapMethodArgument(i));}
        }
        @Override public void visit(int version,int access,String name,String signature,String parent,String[] interfaces){this.name=name.replace('/','.');type(parent);signature(signature);if(interfaces!=null)for(String i:interfaces)type(i);}
        @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){if(descriptor.equals("Ljakarta/persistence/Entity;"))entity=true;return annotation(descriptor);}
        @Override public FieldVisitor visitField(int access,String name,String descriptor,String signature,Object value){signature(descriptor);signature(signature);constant(value);return new FieldVisitor(Opcodes.ASM9){@Override public AnnotationVisitor visitAnnotation(String d,boolean v){return annotation(d);}};}
        @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
            signature(descriptor);signature(signature);if(exceptions!=null)for(String e:exceptions)type(e);
            return new MethodVisitor(Opcodes.ASM9){
                @Override public AnnotationVisitor visitAnnotation(String d,boolean v){return annotation(d);}
                @Override public AnnotationVisitor visitParameterAnnotation(int p,String d,boolean v){return annotation(d);}
                @Override public void visitTypeInsn(int opcode,String type){Dependencies.this.type(type);signature(type);}
                @Override public void visitFieldInsn(int opcode,String owner,String name,String descriptor){type(owner);signature(descriptor);}
                @Override public void visitMethodInsn(int opcode,String owner,String name,String descriptor,boolean isInterface){type(owner);signature(descriptor);}
                @Override public void visitLdcInsn(Object value){constant(value);}
                @Override public void visitInvokeDynamicInsn(String name,String descriptor,Handle handle,Object... arguments){signature(descriptor);constant(handle);for(Object a:arguments)constant(a);}
                @Override public void visitTryCatchBlock(Label start,Label end,Label handler,String type){Dependencies.this.type(type);}
            };
        }
    }
}
