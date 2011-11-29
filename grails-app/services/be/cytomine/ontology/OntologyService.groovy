package be.cytomine.ontology

import be.cytomine.Exception.CytomineException
import be.cytomine.ModelService
import be.cytomine.command.ontology.AddOntologyCommand
import be.cytomine.command.ontology.DeleteOntologyCommand
import be.cytomine.command.ontology.EditOntologyCommand
import be.cytomine.security.User
import be.cytomine.command.AddCommand
import be.cytomine.command.EditCommand
import be.cytomine.command.DeleteCommand
import org.codehaus.groovy.grails.web.json.JSONObject
import be.cytomine.Exception.ObjectNotFoundException
import be.cytomine.Exception.ConstraintException
import be.cytomine.project.Project

class OntologyService extends ModelService {

    static transactional = true
    def springSecurityService
    def transactionService
    def commandService
    def termService
    def cytomineService
    def domainService

    boolean saveOnUndoRedoStack = true

    def list() {
        return Ontology.list()
    }

    def listLight() {
        def ontologies = Ontology.list()
        def data = []
        ontologies.each { ontology ->
            def ontologymap = [:]
            ontologymap.id = ontology.id
            ontologymap.name = ontology.name
            data << ontologymap
        }
        return data
    }

    def listByTerm(Term term) {
        return term?.ontology
    }

    def listByUserLight(User user) {
        def ontologies = user.ontologies()
        def data = []
        ontologies.each { ontology ->
            def ontologymap = [:]
            ontologymap.id = ontology.id
            ontologymap.name = ontology.name
            data << ontologymap
        }
        return data
    }

    def listByUser(User user) {
        return user?.ontologies()
    }

    Ontology read(def id) {
        return Ontology.read(id)
    }

    Ontology get(def id) {
        return Ontology.get(id)
    }

    def add(def json) throws CytomineException {
        User currentUser = cytomineService.getCurrentUser()
        return executeCommand(new AddCommand(user: currentUser), json)
    }

    def update(def json) throws CytomineException {
        User currentUser = cytomineService.getCurrentUser()
        return executeCommand(new EditCommand(user: currentUser), json)
    }



    def delete(def json) throws CytomineException {
        //Start transaction
        transactionService.start()
        User currentUser = cytomineService.getCurrentUser()
        //Read ontology
        Ontology ontology = Ontology.read(json.id)

        //Delete each term from ontology (if possible)
        if (ontology) {
            log.info "Delete term from ontology"
            def terms = ontology.terms()
            terms.each { term ->
                termService.deleteTermRestricted(term.id, currentUser, false)
            }
        }
        //Delete ontology
        log.info "Delete ontology"
        def result = executeCommand(new DeleteCommand(user: currentUser), json)

        //Stop transaction
        transactionService.stop()

        return result
    }

    /**
     * Restore domain which was previously deleted
     * @param json domain info
     * @param commandType command name (add/delete/...) which execute this method
     * @param printMessage print message or not
     * @return response
     */
    def restore(JSONObject json, String commandType, boolean printMessage) {
        restore(Ontology.createFromDataWithId(json),commandType,printMessage)
    }
    def restore(Ontology domain, String commandType, boolean printMessage) {
        //Save new object
        domainService.saveDomain(domain)
        //Build response message
        return responseService.createResponseMessage(domain,[domain.id, domain.name],printMessage,commandType,domain.getCallBack())
    }
    /**
     * Destroy domain which was previously added
     * @param json domain info
     * @param commandType command name (add/delete/...) which execute this method
     * @param printMessage print message or not
     * @return response
     */
    def destroy(JSONObject json, String commandType, boolean printMessage) {
        //Get object to delete
         destroy(Ontology.get(json.id),commandType,printMessage)
    }
    def destroy(Ontology domain, String commandType, boolean printMessage) {
        if (domain && Project.findAllByOntology(domain).size() > 0) throw new ConstraintException("Ontology is still map with project")
        //Build response message
        def response = responseService.createResponseMessage(domain,[domain.id, domain.name],printMessage,commandType,domain.getCallBack())
        //Delete object
        domainService.deleteDomain(domain)
        return response
    }

    /**
     * Edit domain which was previously edited
     * @param json domain info
     * @param commandType  command name (add/delete/...) which execute this method
     * @param printMessage  print message or not
     * @return response
     */
    def edit(JSONObject json, String commandType, boolean printMessage) {
        //Rebuilt previous state of object that was previoulsy edited
        edit(fillDomainWithData(new Ontology(),json),commandType,printMessage)
    }
    def edit(Ontology domain, String commandType, boolean printMessage) {
        //Build response message
        def response = responseService.createResponseMessage(domain,[domain.id, domain.name],printMessage,commandType,domain.getCallBack())
        //Save update
        domainService.saveDomain(domain)
        return response
    }

    /**
     * Create domain from JSON object
     * @param json JSON with new domain info
     * @return new domain
     */
    Ontology createFromJSON(def json) {
       return Ontology.createFromData(json)
    }

    /**
     * Retrieve domain thanks to a JSON object
     * @param json JSON with new domain info
     * @return domain retrieve thanks to json
     */
    def retrieve(JSONObject json) {
        Ontology ontology = Ontology.get(json.id)
        if(!ontology) throw new ObjectNotFoundException("Ontology " + json.id + " not found")
        return ontology
    }

}
