package views;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.Stack;

public class ProductoForm extends JPanel {
    private JTextField txtId, txtDescripcion, txtCategoria, txtPrecio;
    private JTable tabla;
    private DefaultTableModel modeloTabla;
    private int filaSeleccionada = -1;

    // Conexión a MariaDB
    private Connection conexion = null;

    // Pilas para el Historial de Comandos (Deshacer / Rehacer)
    private Stack<ComandoCRUD> pilaDeshacer = new Stack<>();
    private Stack<ComandoCRUD> pilaRehacer = new Stack<>();

    public ProductoForm() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Panel Superior: Formulario y Operaciones ---
        JPanel panelSuperior = new JPanel(new GridLayout(1, 2, 10, 0));

        // Formulario
        JPanel panelDatos = new JPanel(new GridLayout(4, 2, 5, 5));
        panelDatos.setBorder(BorderFactory.createTitledBorder("Datos del Producto"));
        
        txtId = new JTextField(); txtId.setEditable(false);
        txtDescripcion = new JTextField();
        txtCategoria = new JTextField();
        txtPrecio = new JTextField();

        panelDatos.add(new JLabel("ID:")); panelDatos.add(txtId);
        panelDatos.add(new JLabel("DESCRIPCIÓN:")); panelDatos.add(txtDescripcion);
        panelDatos.add(new JLabel("CATEGORÍA:")); panelDatos.add(txtCategoria);
        panelDatos.add(new JLabel("PRECIO:")); panelDatos.add(txtPrecio);

        // Operaciones
        JPanel panelBotones = new JPanel(new GridLayout(4, 1, 5, 5));
        panelBotones.setBorder(BorderFactory.createTitledBorder("Operaciones"));
        
        JButton btnAgregar = new JButton("AGREGAR");
        JButton btnListar = new JButton("LISTAR");
        JButton btnUpdate = new JButton("UPDATE");
        JButton btnDelete = new JButton("DELETE");

        panelBotones.add(btnAgregar); panelBotones.add(btnListar);
        panelBotones.add(btnUpdate); panelBotones.add(btnDelete);

        panelSuperior.add(panelDatos);
        panelSuperior.add(panelBotones);

        // --- Panel Control (EDITAR, NUEVO, DESHACER ACCIÓN, REHACER ACCIÓN) ---
        JPanel panelControl = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnEditar = new JButton("EDITAR");
        JButton btnNuevo = new JButton("NUEVO");
        
        JButton btnAtras = new JButton("<< DESHACER ACCIÓN");
        JButton btnAdelante = new JButton("REHACER ACCIÓN >>");
        
        panelControl.add(btnEditar); 
        panelControl.add(btnNuevo); 
        panelControl.add(btnAtras); 
        panelControl.add(btnAdelante);

        JPanel panelNorte = new JPanel(new BorderLayout());
        panelNorte.add(panelSuperior, BorderLayout.CENTER);
        panelNorte.add(panelControl, BorderLayout.SOUTH);

        // --- Tabla ---
        JPanel panelTabla = new JPanel(new BorderLayout());
        panelTabla.setBorder(BorderFactory.createTitledBorder("Detalle de Productos"));

        String[] columnas = {"ID", "DESCRIPCIÓN", "CATEGORÍA", "PRECIO"};
        modeloTabla = new DefaultTableModel(columnas, 0);
        tabla = new JTable(modeloTabla);
        panelTabla.add(new JScrollPane(tabla), BorderLayout.CENTER);

        add(panelNorte, BorderLayout.NORTH);
        add(panelTabla, BorderLayout.CENTER);

        // Conectar a MariaDB y cargar datos iniciales
        conectarBaseDatos();
        cargarDatosTabla();

        // --- LÓGICA DE BOTÓN DESHACER ACCIÓN ---
        btnAtras.addActionListener(e -> {
            if (!pilaDeshacer.isEmpty()) {
                ComandoCRUD comando = pilaDeshacer.pop();
                comando.deshacer(conexion); // Revierte la acción en la BD
                pilaRehacer.push(comando);  // Se mueve a la pila de rehacer
                cargarDatosTabla();         // Actualiza la tabla visual
                limpiarCampos();
            } else {
                JOptionPane.showMessageDialog(this, "No hay más acciones que deshacer.");
            }
        });

        // --- LÓGICA DE BOTÓN REHACER ACCIÓN ---
        btnAdelante.addActionListener(e -> {
            if (!pilaRehacer.isEmpty()) {
                ComandoCRUD comando = pilaRehacer.pop();
                comando.rehacer(conexion);  // Vuelve a aplicar la acción en la BD
                pilaDeshacer.push(comando); // Se mueve de nuevo a deshacer
                cargarDatosTabla();         // Actualiza la tabla visual
                limpiarCampos();
            } else {
                JOptionPane.showMessageDialog(this, "No hay acciones que rehacer.");
            }
        });

        // --- OPERACIONES DEL CRUD ---

        btnAgregar.addActionListener(e -> {
            if(txtDescripcion.getText().isEmpty() || txtPrecio.getText().isEmpty()) return;
            try {
                // Para guardar el historial necesitamos generar el ID manualmente o capturar el generado
                String sql = "INSERT INTO producto (descripcion, categoria, precio) VALUES (?, ?, ?)";
                PreparedStatement ps = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, txtDescripcion.getText());
                ps.setString(2, txtCategoria.getText());
                ps.setDouble(3, Double.parseDouble(txtPrecio.getText()));
                ps.executeUpdate();
                
                // Obtener el ID generado por la Base de Datos
                ResultSet rsKeys = ps.getGeneratedKeys();
                int idGenerado = -1;
                if(rsKeys.next()) idGenerado = rsKeys.getInt(1);

                // REGISTRAR EN EL HISTORIAL
                ComandoCRUD comando = new ComandoCRUD("INSERT", idGenerado, 
                        txtDescripcion.getText(), txtCategoria.getText(), Double.parseDouble(txtPrecio.getText()));
                pilaDeshacer.push(comando);
                pilaRehacer.clear(); // Cada acción nueva rompe la cadena de rehacer

                cargarDatosTabla();
                limpiarCampos();
            } catch (SQLException ex) {
                ex.printStackTrace();
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
                txtDescripcion.setText(modeloTabla.getValueAt(filaSeleccionada, 1).toString());
                txtCategoria.setText(modeloTabla.getValueAt(filaSeleccionada, 2).toString());
                txtPrecio.setText(modeloTabla.getValueAt(filaSeleccionada, 3).toString());
            }
        });

        btnUpdate.addActionListener(e -> {
            if(txtId.getText().isEmpty()) return;
            try {
                int id = Integer.parseInt(txtId.getText());
                
                // Obtenemos los valores antiguos directamente de la fila seleccionada antes de cambiarla
                String descVieja = modeloTabla.getValueAt(filaSeleccionada, 1).toString();
                String catVieja = modeloTabla.getValueAt(filaSeleccionada, 2).toString();
                double precioViejo = Double.parseDouble(modeloTabla.getValueAt(filaSeleccionada, 3).toString());

                String sql = "UPDATE producto SET descripcion=?, categoria=?, precio=? WHERE id=?";
                PreparedStatement ps = conexion.prepareStatement(sql);
                ps.setString(1, txtDescripcion.getText());
                ps.setString(2, txtCategoria.getText());
                ps.setDouble(3, Double.parseDouble(txtPrecio.getText()));
                ps.setInt(4, id);
                ps.executeUpdate();
                
                // REGISTRAR EN EL HISTORIAL (Guardamos datos nuevos y viejos)
                ComandoCRUD comando = new ComandoCRUD("UPDATE", id, txtDescripcion.getText(), txtCategoria.getText(), Double.parseDouble(txtPrecio.getText()));
                comando.setValoresAnteriores(descVieja, catVieja, precioViejo);
                
                pilaDeshacer.push(comando);
                pilaRehacer.clear();

                cargarDatosTabla();
                limpiarCampos();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        btnDelete.addActionListener(e -> {
            if (filaSeleccionada >= 0) {
                try {
                    int id = (int) modeloTabla.getValueAt(filaSeleccionada, 0);
                    String desc = modeloTabla.getValueAt(filaSeleccionada, 1).toString();
                    String cat = modeloTabla.getValueAt(filaSeleccionada, 2).toString();
                    double precio = Double.parseDouble(modeloTabla.getValueAt(filaSeleccionada, 3).toString());

                    String sql = "DELETE FROM producto WHERE id=?";
                    PreparedStatement ps = conexion.prepareStatement(sql);
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    
                    // REGISTRAR EN EL HISTORIAL (Guardamos lo eliminado para poder reinsertarlo)
                    ComandoCRUD comando = new ComandoCRUD("DELETE", id, desc, cat, precio);
                    pilaDeshacer.push(comando);
                    pilaRehacer.clear();

                    cargarDatosTabla();
                    limpiarCampos();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        });

        btnNuevo.addActionListener(e -> limpiarCampos());
        btnListar.addActionListener(e -> cargarDatosTabla());
    }

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
            ResultSet rs = sentencia.executeQuery("SELECT * FROM producto");
            modeloTabla.setRowCount(0);
            while (rs.next()) {
                modeloTabla.addRow(new Object[]{
                    rs.getInt("id"), rs.getString("descripcion"), rs.getString("categoria"), rs.getDouble("precio")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void limpiarCampos() {
        txtId.setText(""); txtDescripcion.setText(""); txtCategoria.setText(""); txtPrecio.setText("");
        tabla.clearSelection(); filaSeleccionada = -1;
    }
}

// ============================================================================
// CLASE AUXILIAR: Objeto comando que recuerda qué se hizo y cómo revertirlo
// ============================================================================
class ComandoCRUD {
    private String tipoAccion; // "INSERT", "UPDATE", "DELETE"
    private int id;
    private String descripcion, categoria;
    private double precio;
    
    // Variables secundarias sólo para el UPDATE
    private String descAntigua, catAntigua;
    private double precioAntiguo;

    public ComandoCRUD(String tipoAccion, int id, String descripcion, String categoria, double precio) {
        this.tipoAccion = tipoAccion;
        this.id = id;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.precio = precio;
    }

    public void setValoresAnteriores(String descAntigua, String catAntigua, double precioAntiguo) {
        this.descAntigua = descAntigua;
        this.catAntigua = catAntigua;
        this.precioAntiguo = precioAntiguo;
    }

    // LÓGICA PARA EL BOTÓN DESHACER
    public void deshacer(Connection con) {
        try {
            PreparedStatement ps;
            switch (tipoAccion) {
                case "INSERT":
                    // Si se insertó, deshacer significa borrarlo
                    ps = con.prepareStatement("DELETE FROM producto WHERE id = ?");
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    break;
                    
                case "DELETE":
                    // Si se eliminó, deshacer significa volverlo a insertar con su ID original
                    ps = con.prepareStatement("INSERT INTO producto (id, descripcion, categoria, precio) VALUES (?, ?, ?, ?)");
                    ps.setInt(1, id);
                    ps.setString(2, descripcion);
                    ps.setString(3, categoria);
                    ps.setDouble(4, precio);
                    ps.executeUpdate();
                    break;
                    
                case "UPDATE":
                    // Si se actualizó, deshacer significa devolverle los valores viejos
                    ps = con.prepareStatement("UPDATE producto SET descripcion=?, categoria=?, precio=? WHERE id=?");
                    ps.setString(1, descAntigua);
                    ps.setString(2, catAntigua);
                    ps.setDouble(3, precioAntiguo);
                    ps.setInt(4, id);
                    ps.executeUpdate();
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // LÓGICA PARA EL BOTÓN REHACER
    public void rehacer(Connection con) {
        try {
            PreparedStatement ps;
            switch (tipoAccion) {
                case "INSERT":
                    // Volver a insertar lo que el "Deshacer" borró
                    ps = con.prepareStatement("INSERT INTO producto (id, descripcion, categoria, precio) VALUES (?, ?, ?, ?)");
                    ps.setInt(1, id);
                    ps.setString(2, descripcion);
                    ps.setString(3, categoria);
                    ps.setDouble(4, precio);
                    ps.executeUpdate();
                    break;
                    
                case "DELETE":
                    // Volver a borrar lo que el "Deshacer" restauró
                    ps = con.prepareStatement("DELETE FROM producto WHERE id = ?");
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    break;
                    
                case "UPDATE":
                    // Volver a aplicar los datos nuevos de la edición
                    ps = con.prepareStatement("UPDATE producto SET descripcion=?, categoria=?, precio=? WHERE id=?");
                    ps.setString(1, descripcion);
                    ps.setString(2, categoria);
                    ps.setDouble(3, precio);
                    ps.setInt(4, id);
                    ps.executeUpdate();
                    break;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}