package com.fractalos.modules.vfs;
import java.util.LinkedHashMap;
import java.util.Map;

//El sistema de archivos es una estructura de datos tipo árbol. Un árbol se modela mediante nodos y adyacencias.
//En este caso un nodo es un directorio que contiene otros archivos que pueden ser o no ser directorios también.
//Cuando creamos un directorio dentro de otro, lo que en realidad se hace es generar una adyacencia jerarquica, donde
//el directorio superior es padre del directorio que contiene, el directorio hijo es dependiente del directorio padre.

public class FileNode {
    private String name; //Nombre del archivo.
    private boolean isDir; //Es directorio.
    private int sizeKB; //Tamaño KB.
    private String content; //Contenido del archivo.
    private FileNode parent; //Dirección del directorio que lo contiene.
    private Map<String, FileNode> childs; //Contenido del directorio.

    //Constructor para directorios.
    public FileNode(String name, FileNode parent) {
        this.name = name;
        this.isDir = true;
        this.sizeKB = 4; //Para simular métadatos de un directorio.
        this.content = null;
        this.parent = parent;
        this.childs= new LinkedHashMap<>(); //LinkedHashMap para preservar el orden de creación al hacer un 'ls'.
    }

    //Constructor para archivos.
    public FileNode(String name, int sizeKB, String content, FileNode parent) {
        this.name = name;
        this.isDir = false;
        this.sizeKB = sizeKB;
        this.content = content;
        this.parent = parent;
        this.childs = null; //Un documento no puede contener otros archivos dentro.
    }

    //Getters y Setters.
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDirectory() {
        return isDir;
    }

    public int getSizeKB() {
        return sizeKB;
    }

    public void setSizeKB(int sizeKB) {
        this.sizeKB = sizeKB;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public FileNode getParent() {
        return parent;
    }

    //Métodos de navegación y control del árbol.
    public Map<String, FileNode> getChildren() { //Regresa un Map con los archivos hijos de un directorio.
        return childs;
    }

    public void addChild(FileNode child) { //Agrega un hijo y actualiza el tamaño de la carpeta padre.
        if (this.isDir) {
            this.childs.put(child.getName(), child);
            this.sizeKB += child.getSizeKB();
        }
    }

    public FileNode getChild(String childName) { //Busca un hijo por su nombre.
        if (this.isDir && this.childs.containsKey(childName)) {
            return this.childs.get(childName);
        }
        return null;
    }

    public void removeChild(String childName) { //Elimina un hijo y actualiza el tamaño de la carpeta padre.
        if (this.isDir && this.childs.containsKey(childName)) {
            FileNode removed = this.childs.remove(childName);
            this.sizeKB -= removed.getSizeKB();
        }
    }
}
