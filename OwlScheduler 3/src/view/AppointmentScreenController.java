package view;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import owlschedule.DBConnection;
import owlschedule.ScheduleOwl;
import model.Appointment;
import model.Customer;
import model.User;

public class AppointmentScreenController {
     
    @FXML
    private TableView<Appointment> apptTableView;

    @FXML
    private TableColumn<Appointment, ZonedDateTime> startApptColumn;

    @FXML
    private TableColumn<Appointment, LocalDateTime> endApptColumn;
    
    @FXML
    private TableColumn<Appointment, String> titleApptColumn;

    @FXML
    private TableColumn<Appointment, String> typeApptColumn;

    @FXML
    private TableColumn<Appointment, Customer> customerApptColumn;

    @FXML
    private TableColumn<Appointment, String> consultantApptColumn;

    @FXML
    private RadioButton weekRadioButton;

    @FXML
    private RadioButton monthRadioButton;
    
    @FXML
    private ToggleGroup apptToggleGroup;
    
    private ScheduleOwl mainApp;
    private User currentUser;
    private final DateTimeFormatter timeDTF = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    private final ZoneId newzid = ZoneId.systemDefault();
    ObservableList<Appointment> apptList;
    
    /**
     * Initializes Appointment Screen 
     * @param mainApp
     * @param currentUser 
     */    
    public void setAppointmentScreen(ScheduleOwl mainApp, User currentUser) {
	this.mainApp = mainApp;
        this.currentUser = currentUser;
        
        apptToggleGroup = new ToggleGroup();
        this.weekRadioButton.setToggleGroup(apptToggleGroup);
        this.monthRadioButton.setToggleGroup(apptToggleGroup);
        
        startApptColumn.setCellValueFactory(new PropertyValueFactory<>("start"));
        endApptColumn.setCellValueFactory(new PropertyValueFactory<>("end"));
        titleApptColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        typeApptColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        customerApptColumn.setCellValueFactory(new PropertyValueFactory<>("customer"));
        consultantApptColumn.setCellValueFactory(new PropertyValueFactory<>("user"));
        
        apptList = FXCollections.observableArrayList();
        populateAppointmentList();
        apptTableView.getItems().setAll(apptList);        
    }
    
    /**
     * Filters to show appointments from current date to a month out
     * @param event 
     */
    @FXML
    void handleApptMonth(ActionEvent event) {
        
        LocalDate now = LocalDate.now();
        LocalDate nowPlus1Month = now.plusMonths(1);
        
        FilteredList<Appointment> filteredData = new FilteredList<>(apptList);
        filteredData.setPredicate(row -> {

            LocalDate rowDate = LocalDate.parse(row.getStart(), timeDTF);

            return rowDate.isAfter(now.minusDays(1)) && rowDate.isBefore(nowPlus1Month);
        });
        apptTableView.setItems(filteredData);

    }
    
    /**
     * Filters to show appointments from current date to a week out
     * @param event 
     */
    @FXML
    void handleApptWeek(ActionEvent event) {
        
        LocalDate now = LocalDate.now();
        LocalDate nowPlus7 = now.plusDays(7);
        FilteredList<Appointment> filteredData = new FilteredList<>(apptList);
        filteredData.setPredicate(row -> {

            LocalDate rowDate = LocalDate.parse(row.getStart(), timeDTF);

            return rowDate.isEqual(now.minusDays(1)) && rowDate.isBefore(nowPlus7);
        });
        apptTableView.setItems(filteredData);
    }
    
    
    @FXML
    void handleDeleteAppt(ActionEvent event) {
        Appointment selectedAppointment = apptTableView.getSelectionModel().getSelectedItem();
        
        if (selectedAppointment != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Deletion");
            alert.setHeaderText("Are you sure you want to delete " + selectedAppointment.getTitle() + " scheduled for " + selectedAppointment.getStart() + "?");
            alert.showAndWait()
            .filter(response -> response == ButtonType.OK)
            .ifPresent(response -> {
                deleteAppointment(selectedAppointment);
                mainApp.showAppointmentScreen(currentUser);
                }
            );
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Appointment selected for Deletion");
            alert.setContentText("Please select an Appointment in the Table to delete");
            alert.showAndWait();
        }

    }

    @FXML
    void handleEditAppt(ActionEvent event) {
        Appointment selectedAppointment = apptTableView.getSelectionModel().getSelectedItem();
        
        if (selectedAppointment != null) {
            boolean okClicked = mainApp.showEditApptScreen(selectedAppointment, currentUser);
            mainApp.showAppointmentScreen(currentUser);
            
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Appointment selected");
            alert.setContentText("Please select an Appointment in the Table");
            alert.showAndWait();
        }

    }

    @FXML
    void handleNewAppt(ActionEvent event) throws IOException{
        boolean okClicked = mainApp.showNewApptScreen(currentUser);
        mainApp.showAppointmentScreen(currentUser);
    }
    
    private void populateAppointmentList() {
      
        try{            
            
        PreparedStatement statement = DBConnection.getConn().prepareStatement(
        "SELECT appointment.appointmentId, appointment.customerId, appointment.title, appointment.description, "
                + "appointment.`start`, appointment.`end`, customer.customerId, customer.customerName, appointment.createdBy "
                + "FROM appointment, customer "
                + "WHERE appointment.customerId = customer.customerId "
                + "ORDER BY `start`");
            ResultSet rs = statement.executeQuery();
           
            
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
                      
                apptList.add(new Appointment(tAppointmentId, newLocalStart.format(timeDTF), newLocalEnd.format(timeDTF), tTitle, tType, tCustomer, tUser));
                

            }
            
        } catch (SQLException sqe) {
            System.out.println("Check your SQL");
            sqe.printStackTrace();
        } catch (Exception e) {
            System.out.println("Something besides the SQL went wrong.");
        }

    }
    
    private void deleteAppointment(Appointment appointment) {
        try{           
            PreparedStatement pst = DBConnection.getConn().prepareStatement("DELETE appointment.* FROM appointment WHERE appointment.appointmentId = ?");
            pst.setString(1, appointment.getAppointmentId()); 
            pst.executeUpdate();  
                
        } catch(SQLException e){
            e.printStackTrace();
        }       
    }

    
    
}
