package com.fractalos.boot;

//Aquí ira la clase main que arranca todo el sistema

import com.fractalos.modules.shell.Shell;

//Hola Aroncio :b

public class BootLoader {
    public static void main(String args[]){
        System.out.println("Hola equipo :)");
        System.out.println("Este es el primer pull en Github :O");
        javax.swing.SwingUtilities.invokeLater(new Runnable(){
            @Override
            public void run(){
                Shell miterminal = new Shell();
                miterminal.mostrar();
            }
        });
    }
}
