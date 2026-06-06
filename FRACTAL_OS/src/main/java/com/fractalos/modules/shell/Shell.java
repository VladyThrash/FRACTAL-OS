package com.fractalos.modules.shell;

import javax.swing.*;

import  javax.swing.*;
import  java.awt.*;
import  java.awt.event.ActionEvent;
import  java.awt.event.ActionListener;

public class Shell{
    private JFrame ventana;
    private  JTextArea areaHistorial;
    private JTextField campoEntrada;

    public Shell(){
        ventana = new JFrame("FracShell");
        ventana.setSize(600,400);
        ventana.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        ventana.setLayout(new BorderLayout());

        areaHistorial = new JTextArea();
        areaHistorial.setBackground(Color.BLACK);
        areaHistorial.setForeground(Color.GREEN);
        areaHistorial.setFont(new Font("Consolas", Font.PLAIN, 14));
        areaHistorial.setEditable(false);

        JScrollPane scroll = new JScrollPane(areaHistorial);
        ventana.add(scroll, BorderLayout.CENTER);

        campoEntrada = new JTextField();
        campoEntrada.setBackground(Color.BLACK);
        campoEntrada.setForeground(Color.WHITE);
        campoEntrada.setCaretColor(Color.WHITE);
        campoEntrada.setFont(new Font("Consolas", Font.PLAIN,14));
        ventana.add(campoEntrada, BorderLayout.SOUTH);

        campoEntrada.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e){
                String comandoCompleto = campoEntrada.getText();
                if (!comandoCompleto.trim().isEmpty()) {
                    ejecutarComando(comandoCompleto);
                }
                campoEntrada.setText(""); 
            }
        });
    }

    public void mostrar(){
        ventana.setVisible(true);
    }

    private void ejecutarComando(String linea){
        areaHistorial.append("user@fracShell:~$ " + linea + "\n");

        String respuesta = procesarTexto(linea);
        areaHistorial.append(respuesta + "\n");

    }

    private String procesarTexto(String linea) {
        // Aquí va la lógica del punto 3
        return "Comando recibido."; 
    }
    //Read: El sistema espera a que el usuario escriba algo y presione Enter.

    //Eval: Tu código analiza el texto (identifica el comando y sus argumentos).

    //Print: Se ejecuta la acción y se muestra el resultado en la pantalla.

    //Loop
}
