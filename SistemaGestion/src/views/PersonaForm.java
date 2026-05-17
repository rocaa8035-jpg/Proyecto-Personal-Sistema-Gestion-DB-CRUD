package views;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.Stack;

public class PersonaForm extends JPanel {
    private JTextField txtId, txtNombre, txtRuc, txtDireccion, txtTelefono;
    private JTable tabla;
    private DefaultTableModel modeloTabla;
    private int filaSeleccionada = -1;

    // Conexión a MariaDB
    private Connection conexion = null;

    // Pilas para el Historial de Comandos (Deshacer / Rehacer)
    private Stack<ComandoPersona> pilaDeshacer = new Stack<>();
    private Stack<ComandoPersona> pilaRehacer = new Stack<>();

    public PersonaForm() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Panel Superior: Formulario y Operaciones ---
        JPanel panelSuperior = new JPanel(new GridLayout(1, 2, 10, 0));

        // Formulario (Ingresar Datos)
        JPanel panelDatos = new JPanel(new GridLayout(5, 2, 5, 5));
        panelDatos.setBorder(BorderFactory.createTitledBorder("Ingresar Datos"));
        
        txtId = new JTextField(); txtId.setEditable(false); 
        txtNombre = new JTextField();
        txtRuc = new JTextField();
        txtDireccion = new JTextField();
        txtTelefono = new JTextField();

        panelDatos.add(new JLabel("ID:")); panelDatos.add(txtId);
        panelDatos.add(new JLabel("NOMBRES:")); panelDatos.add(txtNombre);
        panelDatos.add(new JLabel("NUM RUC:")); panelDatos.add(txtRuc);
        panelDatos.add(new JLabel("DIRECCIÓN:")); panelDatos.add(txtDireccion);
        panelDatos.add(new JLabel("TELÉFONO:")); panelDatos.add(txtTelefono);

        // Botones de Operaciones
        JPanel panelBotones = new JPanel(new GridLayout(4, 1, 5, 5));
        panelBotones.setBorder(BorderFactory.createTitledBorder("Operaciones"));
        
        JButton btnAgregar = new JButton("AGREGAR");
        JButton btnListar = new JButton("LISTAR");
        JButton btnUpdate = new JButton("UPDATE");
        JButton btnDelete = new JButton("DELETE");

        panelBotones.add(btnAgregar);
        panelBotones.add(btnListar);
        panelBotones.add(btnUpdate);
        panelBotones.add(btnDelete);

        panelSuperior.add(panelDatos);
        panelSuperior.add(panelBotones);

        // --- Panel Central: Botones de control con Deshacer y Rehacer ---
        JPanel panelControl = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnEditar = new JButton("EDITAR");
        JButton btnNuevo = new JButton("NUEVO");
        
        JButton btnAtras = new JButton("<< DESHACER ACCIÓN");
        JButton btnAdelante = new JButton("REHACER ACCIÓN >>");
        
        panelControl.add(btnEditar);
        panelControl.add(btnNuevo);
        panelControl.add(btnAtras);     
        panelControl.add(btnAdelante);  

        // Agrupamos superior y control en un contenedor norte
        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(panelSuperior, BorderLayout.CENTER);
        panelNorte.add(panelControl, BorderLayout.SOUTH);

        // --- Panel Inferior: Tabla (Detalle) ---
        JPanel panelTabla = new JPanel(new BorderLayout());
        panelTabla.setBorder(BorderFactory.createTitledBorder("Detalle"));

        String[] columnas = {"ID", "NOMBRES", "NUM RUC", "DIRECCIÓN", "TELÉFONO"};
        modeloTabla = new DefaultTableModel(columnas, 0);
        tabla = new JTable(modeloTabla);
        panelTabla.add(new JScrollPane(tabla), BorderLayout.CENTER);

        // Agregar todo al panel principal
        add(panelNorte, BorderLayout.NORTH);
        add(panelTabla, BorderLayout.CENTER);

        // Conectar a MariaDB y cargar datos
        conectarBaseDatos();
        cargarDatosTabla(); 

        // --- LÓGICA DE BOTÓN DESHACER ACCIÓN ---
        btnAtras.addActionListener(e -> {
            if (!pilaDeshacer.isEmpty()) {
                ComandoPersona comando = pilaDeshacer.pop();
                comando.deshacer(conexion); // Revierte la acción en la BD
                pilaRehacer.push(comando);  // Se mueve a la de rehacer
                cargarDatosTabla();         // Recarga la tabla de la GUI
                limpiarCampos();
            } else {
                JOptionPane.showMessageDialog(this, "No hay más acciones que deshacer.");
            }
        });

        // --- LÓGICA DE BOTÓN REHACER ACCIÓN ---
        btnAdelante.addActionListener(e -> {
            if (!pilaRehacer.isEmpty()) {
                ComandoPersona comando = pilaRehacer.pop();
                comando.rehacer(conexion);  // Aplica la acción de nuevo
                pilaDeshacer.push(comando); // Vuelve a la pila de deshacer
                cargarDatosTabla();         
                limpiarCampos();
            } else {
                JOptionPane.showMessageDialog(this, "No hay acciones que rehacer.");
            }
        });

        // --- OPERACIONES DEL CRUD ---
        
        btnAgregar.addActionListener(e -> {
            if(txtNombre.getText().isEmpty()) return;
            try {
                String sql = "INSERT INTO cliente (nombre, numruc, direccion, telefono) VALUES (?, ?, ?, ?)";
                PreparedStatement ps = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, txtNombre.getText());
                ps.setString(2, txtRuc.getText());
                ps.setString(3, txtDireccion.getText());
                ps.setString(4, txtTelefono.getText());
                ps.executeUpdate();
                
                // Capturar el ID generado automáticamente por MariaDB
                ResultSet rsKeys = ps.getGeneratedKeys();
                int idGenerado = -1;
                if(rsKeys.next()) idGenerado = rsKeys.getInt(1);

                // REGISTRAR ACCIÓN EN EL HISTORIAL
                ComandoPersona comando = new ComandoPersona("INSERT", idGenerado, 
                        txtNombre.getText(), txtRuc.getText(), txtDireccion.getText(), txtTelefono.getText());
                pilaDeshacer.push(comando);
                pilaRehacer.clear(); // Las acciones nuevas rompen el flujo de Rehacer

                cargarDatosTabla(); 
                limpiarCampos();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error al guardar: " + ex.getMessage());
            }
        });

        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                filaSeleccionada = tabla.getSelectedRow();
            }
        });

        btnEditar.addActionListener(e -> {
            if (filaSeleccionada >= 0) {
                txtId.setText(modeloTabla.getValueAt(filaSeleccionada, 0).toString());
                txtNombre.setText(modeloTabla.getValueAt(filaSeleccionada, 1).toString());
                txtRuc.setText(modeloTabla.getValueAt(filaSeleccionada, 2).toString());
                txtDireccion.setText(modeloTabla.getValueAt(filaSeleccionada, 3).toString());
                txtTelefono.setText(modeloTabla.getValueAt(filaSeleccionada, 4).toString());
            }
        });

        btnUpdate.addActionListener(e -> {
            if(txtId.getText().isEmpty()) return;
            try {
                int id = Integer.parseInt(txtId.getText());
                
                // Resguardar valores antiguos directo de la JTable para el historial
                String nomViejo = modeloTabla.getValueAt(filaSeleccionada, 1).toString();
                String rucViejo = modeloTabla.getValueAt(filaSeleccionada, 2).toString();
                String dirVieja = modeloTabla.getValueAt(filaSeleccionada, 3).toString();
                String telViejo = modeloTabla.getValueAt(filaSeleccionada, 4).toString();

                String sql = "UPDATE cliente SET nombre=?, numruc=?, direccion=?, telefono=? WHERE id=?";
                PreparedStatement ps = conexion.prepareStatement(sql);
                ps.setString(1, txtNombre.getText());
                ps.setString(2, txtRuc.getText());
                ps.setString(3, txtDireccion.getText());
                ps.setString(4, txtTelefono.getText());
                ps.setInt(5, id);
                ps.executeUpdate();
                
                // REGISTRAR EN EL HISTORIAL
                ComandoPersona comando = new ComandoPersona("UPDATE", id, txtNombre.getText(), txtRuc.getText(), txtDireccion.getText(), txtTelefono.getText());
                comando.setValoresAnteriores(nomViejo, rucViejo, dirVieja, telViejo);
                
                pilaDeshacer.push(comando);
                pilaRehacer.clear();

                cargarDatosTabla();
                limpiarCampos();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error al actualizar: " + ex.getMessage());
            }
        });

        btnDelete.addActionListener(e -> {
            if (filaSeleccionada >= 0) {
                try {
                    int id = (int) modeloTabla.getValueAt(filaSeleccionada, 0);
                    String nom = modeloTabla.getValueAt(filaSeleccionada, 1).toString();
                    String ruc = modeloTabla.getValueAt(filaSeleccionada, 2).toString();
                    String dir = modeloTabla.getValueAt(filaSeleccionada, 3).toString();
                    String tel = modeloTabla.getValueAt(filaSeleccionada, 4).toString();

                    String sql = "DELETE FROM cliente WHERE id=?";
                    PreparedStatement ps = conexion.prepareStatement(sql);
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    
                    // REGISTRAR ELIMINACIÓN EN EL HISTORIAL (Guardamos todo para poder restaurarlo)
                    ComandoPersona comando = new ComandoPersona("DELETE", id, nom, ruc, dir, tel);
                    pilaDeshacer.push(comando);
                    pilaRehacer.clear();

                    cargarDatosTabla();
                    limpiarCampos();
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(this, "Error al eliminar: " + ex.getMessage());
                }
            }
        });

        btnNuevo.addActionListener(e -> limpiarCampos());
        btnListar.addActionListener(e -> cargarDatosTabla());
    }

    // --- MÉTODOS DE CONEXIÓN Y BASE DE DATOS ---

    private void conectarBaseDatos() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            String cadena = "jdbc:mariadb://127.0.0.1:3306/bd302";
            String usuario = "root";
            String clave = "";
            conexion = DriverManager.getConnection(cadena, usuario, clave);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error de conexión: " + e.getMessage());
        }
    }

    private void cargarDatosTabla() {
        try {
            Statement sentencia = conexion.createStatement();
            ResultSet rs = sentencia.executeQuery("SELECT * FROM cliente");
            modeloTabla.setRowCount(0);

            while (rs.next()) {
                modeloTabla.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("nombre"),
                    rs.getString("numruc"),
                    rs.getString("direccion"),
                    rs.getString("telefono")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void limpiarCampos() {
        txtId.setText(""); txtNombre.setText(""); txtRuc.setText(""); txtDireccion.setText(""); txtTelefono.setText("");
        tabla.clearSelection(); filaSeleccionada = -1;
    }
}

// ============================================================================
// CLASE AUXILIAR: Objeto comando que recuerda los movimientos en la tabla personas
// ============================================================================
class ComandoPersona {
    private String tipoAccion; // "INSERT", "UPDATE", "DELETE"
    private int id;
    private String nombre, ruc, direccion, telefono;
    
    // Duplicados para guardar el estado anterior solo si es un UPDATE
    private String nombreAntiguo, rucAntiguo, direccionAntigua, telefonoAntiguo;

    public ComandoPersona(String tipoAccion, int id, String nombre, String ruc, String direccion, String telefono) {
        this.tipoAccion = tipoAccion;
        this.id = id;
        this.nombre = nombre;
        this.ruc = ruc;
        this.direccion = direccion;
        this.telefono = telefono;
    }

    public void setValoresAnteriores(String nombreAntiguo, String rucAntiguo, String direccionAntigua, String telefonoAntiguo) {
        this.nombreAntiguo = nombreAntiguo;
        this.rucAntiguo = rucAntiguo;
        this.direccionAntigua = direccionAntigua;
        this.telefonoAntiguo = telefonoAntiguo;
    }

    // LÓGICA PARA REVERTIR ACCIONES (DESHACER)
    public void deshacer(Connection con) {
        try {
            PreparedStatement ps;
            switch (tipoAccion) {
                case "INSERT":
                    // Si agregaste a alguien, deshacer lo elimina
                    ps = con.prepareStatement("DELETE FROM cliente WHERE id = ?");
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    break;
                    
                case "DELETE":
                    // Si lo eliminaste, deshacer lo vuelve a reinsertar tal cual estaba
                    ps = con.prepareStatement("INSERT INTO cliente (id, nombre, numruc, direccion, telefono) VALUES (?, ?, ?, ?, ?)");
                    ps.setInt(1, id);
                    ps.setString(2, nombre);
                    ps.setString(3, ruc);
                    ps.setString(4, direccion);
                    ps.setString(5, telefono);
                    ps.executeUpdate();
                    break;
                    
                case "UPDATE":
                    // Si modificaste datos, deshacer restaura el estado original previo
                    ps = con.prepareStatement("UPDATE cliente SET nombre=?, numruc=?, direccion=?, telefono=? WHERE id=?");
                    ps.setString(1, nombreAntiguo);
                    ps.setString(2, rucAntiguo);
                    ps.setString(3, direccionAntigua);
                    ps.setString(4, telefonoAntiguo);
                    ps.setInt(5, id);
                    ps.executeUpdate();
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // LÓGICA PARA VOLVER A APLICAR ACCIONES (REHACER)
    public void rehacer(Connection con) {
        try {
            PreparedStatement ps;
            switch (tipoAccion) {
                case "INSERT":
                    // Vuelve a añadir lo que deshacer borró
                    ps = con.prepareStatement("INSERT INTO cliente (id, nombre, numruc, direccion, telefono) VALUES (?, ?, ?, ?, ?)");
                    ps.setInt(1, id);
                    ps.setString(2, nombre);
                    ps.setString(3, ruc);
                    ps.setString(4, direccion);
                    ps.setString(5, telefono);
                    ps.executeUpdate();
                    break;
                    
                case "DELETE":
                    // Vuelve a eliminar lo que deshacer restauró
                    ps = con.prepareStatement("DELETE FROM cliente WHERE id = ?");
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    break;
                    
                case "UPDATE":
                    // Vuelve a colocar los datos editados más nuevos
                    ps = con.prepareStatement("UPDATE cliente SET nombre=?, numruc=?, direccion=?, telefono=? WHERE id=?");
                    ps.setString(1, nombre);
                    ps.setString(2, ruc);
                    ps.setString(3, direccion);
                    ps.setString(4, telefono);
                    ps.setInt(5, id);
                    ps.executeUpdate();
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}