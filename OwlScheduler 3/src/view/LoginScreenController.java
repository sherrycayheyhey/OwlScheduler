package view;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.text.Text;
import owlschedule.DBConnection;
import owlschedule.ScheduleOwl;
import model.Appointment;
import model.Customer;
import model.User;
import owlschedule.LoggerUtil;

public class LoginScreenController {
    
            
    @FXML
    private Label errorMessage;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Text usernameText;

    @FXML
    private Text passwordText;

    @FXML
    private Text titleText;

    @FXML
    private Button signinText;

    @FXML
    private Button cancelText;
    
    // Reference to the main application.
    private ScheduleOwl mainApp;
    ResourceBundle rb = ResourceBundle.getBundle("login", Locale.getDefault());
    private final ZoneId newzid = ZoneId.systemDefault();
    private final DateTimeFormatter timeDTF = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    User user = new User();
    ObservableList<Appointment> reminderList;
    private final static Logger LOGGER = Logger.getLogger(LoggerUtil.class.getName());

    /**
     * The constructor.
     * The constructor is called before the initialize() method.
     */
    public LoginScreenController() {
    }
    
    @FXML
    void handleSignInAction(ActionEvent event) {
        String userN = usernameField.getText();   // Collecting the input
        String pass = passwordField.getText(); // Collecting the input
        
        if(userN.length()==0 || pass.length()==0)  // Checking for empty field
            errorMessage.setText(rb.getString("empty"));
        else{
            
            User validUser = validateLogin(userN,pass); 
            if (validUser == null) {
                errorMessage.setText(rb.getString("incorrect"));
                return;
            }
            populateReminderList();
            reminder();
            mainApp.showMenu(validUser);
            mainApp.showAppointmentScreen(validUser);
            //logs successful logins
            LOGGER.log(Level.INFO, "{0} logged in", validUser.getUsername());
            
            
        }
    }
    
    /**
     * Searches for matching username and password in database
     * @param username
     * @param password
     * @return user if match found
     */
    User validateLogin(String username,String password) {
        try{           
            PreparedStatement pst = DBConnection.getConn().prepareStatement("SELECT * FROM user WHERE userName=? AND password=?");
            pst.setString(1, username); 
            pst.setString(2, password);
            ResultSet rs = pst.executeQuery();                        
            if(rs.next()){
                user.setUsername(rs.getString("userName"));
                user.setPassword(rs.getString("password"));
                user.setUserID(rs.getInt("userId"));
            } else {
                return null;    
            }            
                
        } catch(SQLException e){
            e.printStackTrace();
        }       
        return user;
}
    
    @FXML
    void handleCancelAction(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Cancel");
            alert.setHeaderText("Are you sure you want close the program?");
            alert.showAndWait()
            .filter(response -> response == ButtonType.OK)
            .ifPresent((ButtonType response) -> {
                Platform.exit();
                System.exit(0);
                }
            );
    }
    
    /**
     * Initializes LoginScreen
     * @param mainApp 
     */
    public void setLogin(ScheduleOwl mainApp) {
	this.mainApp = mainApp;
        reminderList = FXCollections.observableArrayList();
        
        titleText.setText(rb.getString("title"));
        usernameText.setText(rb.getString("username"));
        passwordText.setText(rb.getString("password"));
        signinText.setText(rb.getString("signin"));
        cancelText.setText(rb.getString("cancel"));
    }
    
    /**
     * Filters reminder list and launches alert if filteredData is not empty
     */
    private void reminder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nowPlus15Min = now.plusMinutes(15);
        
        FilteredList<Appointment> filteredData = new FilteredList<>(reminderList);

        filteredData.setPredicate(row -> {
            LocalDateTime rowDate = LocalDateTime.parse(row.getStart(), timeDTF);
            return rowDate.isAfter(now.minusMinutes(1)) && rowDate.isBefore(nowPlus15Min);
            }
        );
        if (filteredData.isEmpty()) {
            System.out.println("No reminders");
        } else {
            String type = filteredData.get(0).getDescription();
            String customer =  filteredData.get(0).getCustomer().getCustomerName();
            String start = filteredData.get(0).getStart();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Upcoming Appointment Reminder");
            alert.setHeaderText("Reminder: You have the following appointment set for the next 15 minutes.");
            alert.setContentText("Your upcoming " + type + " appointment with " + customer +
                " is currently set for " + start + ".");
            alert.showAndWait();
        }
        
    }
    
    private void populateReminderList() {
        System.out.println(user.getUsername());
        try{           
        PreparedStatement pst = DBConnection.getConn().prepareStatement(
        "SELECT appointment.appointmentId, appointment.customerId, appointment.title, appointment.description, "
                + "appointment.`start`, appointment.`end`, customer.customerId, customer.customerName, appointment.createdBy "
                + "FROM appointment, customer "
                + "WHERE appointment.customerId = customer.customerId AND appointment.createdBy = ? "
                + "ORDER BY `start`");
            pst.setString(1, user.getUsername());
            ResultSet rs = pst.executeQuery();
           
            
            while (rs.next()) {
                
                String tAppointmentId = rs.getString("appointment.appointmentId");
                Timestamp tsStart = rs.getTimestamp("appointment.start");
                ZonedDateTime newzdtStart = tsStart.toLocalDateTime().atZone(ZoneId.of("UTC"));
        	ZonedDateTime newLocalStart = newzdtStart.withZoneSameInstant(newzid);

                Timestamp tsEnd = rs.getTimestamp("appointment.end");
                ZonedDateTime newzdtEnd = tsEnd.toLocalDateTime().atZone(ZoneId.of("UTC"));
        	ZonedDateTime newLocalEnd = newzdtEnd.withZoneSameInstant(newzid);

                String tTitle = rs.getString("appointment.title");
                
                String tType = rs.getString("appointment.description");
                
                Customer tCustomer = new Customer(rs.getString("appointment.customerId"), rs.getString("customer.customerName"));
                
                String tUser = rs.getString("appointment.createdBy");
                      
                reminderList.add(new Appointment(tAppointmentId, newLocalStart.format(timeDTF), newLocalEnd.format(timeDTF), tTitle, tType, tCustomer, tUser));   

            }   
            
        } catch (SQLException sqe) {
            System.out.println("Check your SQL");
            sqe.printStackTrace();
        } catch (Exception e) {
            System.out.println("Something besides the SQL went wrong.");
            e.printStackTrace();
        }
    }

}
