package com.fractalos.modules.vfs;
import java.util.LinkedHashMap;
import java.util.Map;

//El sistema de archivos es una estructura de datos tipo árbol. Un árbol se modela mediante nodos y adyacencias.
//En este caso un nodo es un directorio que contiene otros archivos que pueden ser o no ser directorios también.
//Cuando creamos un directorio dentro de otro, lo que en realidad se hace es generar una adyacencia jerarquica, donde
//el directorio superior es padre del directorio que contiene, el directorio hijo es dependiente del directorio padre.

public class FileNode {
    public enum Type { //Define si el nodo es un directorio, archivo de texto o ejecutable.
        DIRECTORY,
        TEXT_FILE,
        EXECUTABLE
    }

    private Type type;
    private String name; //Nombre del archivo.
    private int sizeKB; //Tamaño KB.
    private FileNode parent; //Dirección del directorio que lo contiene.
    private Map<String, FileNode> childs; //Contenido del directorio.
    private String content; //Contenido del archivo de texto.
    private int priority; //Prioridad del archivo ejecutable.
    private int bursts; //Número de ráfagas en CPU del ejecutable.
    private int requiredRamKB; //Espacio necesario en memoria del ejecutable.

    //Constructor para directorios.
    public FileNode(String name, FileNode parent) {
        this.name = name;
        this.type = Type.DIRECTORY;
        this.sizeKB = 4;
        this.parent = parent;
        this.childs = new LinkedHashMap<>();
    }

    //Constructor para archivos de texto.
    public FileNode(String name, String content, FileNode parent) {
        this.name = name;
        this.type = Type.TEXT_FILE;
        this.content = content;
        this.sizeKB = Math.max(1, content.length() / 1024); //1 KB mínimo.
        this.parent = parent;
    }

    //Constructor para archivos ejecutables.
    public FileNode(String name, int priority, int bursts, int requiredRamKB, FileNode parent) {
        this.name = name;
        this.type = Type.EXECUTABLE;
        this.priority = priority;
        this.bursts = bursts;
        this.requiredRamKB = requiredRamKB;
        this.sizeKB = requiredRamKB; //El binario pesa lo mismo que la RAM que pide.
        this.parent = parent;
    }

    //Getters y Setters.
    public Type getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public int getPriority() {
        return priority;
    }

    public int getBursts() {
        return bursts;
    }

    public int getRequiredRamKB() {
        return requiredRamKB;
    }

    public String getName() {
        return name;
    }

    public int getSizeKB() {
        return sizeKB;
    }

    public FileNode getParent() {
        return parent;
    }

    //Métodos de navegación y control del árbol.
    public Map<String, FileNode> getChildren() { //Regresa un Map con los archivos hijos de un directorio.
        return childs;
    }

    public void addChild(FileNode child) { //Agrega un hijo y actualiza el tamaño en la carpeta padre.
        if (this.type == Type.DIRECTORY) {
            this.childs.put(child.getName(), child);
            this.sizeKB += child.getSizeKB();
        }
    }

    public FileNode getChild(String childName) { //Obtiene un archivo hijo desde la carpeta del padre.
        if (this.type == Type.DIRECTORY && this.childs.containsKey(childName)) {
            return this.childs.get(childName);
        }
        return null;
    }

    public void removeChild(String childName) { //Elimina un hijo y actualiza el tamaño de la carpeta padre.
        if (this.type == Type.DIRECTORY && this.childs.containsKey(childName)) {
            FileNode removed = this.childs.remove(childName);
            this.sizeKB -= removed.getSizeKB();
        }
    }
}
