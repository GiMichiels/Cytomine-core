package be.cytomine.utils

import be.cytomine.CytomineDomain
import be.cytomine.Exception.InvalidRequestException
import be.cytomine.Exception.ObjectNotFoundException
import be.cytomine.Exception.ServerException
import be.cytomine.Exception.WrongArgumentException
import be.cytomine.SecurityACL
import be.cytomine.command.Command
import be.cytomine.command.DeleteCommand
import grails.util.GrailsNameUtils
import static org.springframework.security.acls.domain.BasePermission.READ

abstract class ModelService {

    static transactional = true

    def responseService
    def commandService
    def cytomineService
    def grailsApplication
    def taskService
    boolean saveOnUndoRedoStack = true

    /**
     * Save a domain on database, throw error if cannot save
     */
    def saveDomain(def newObject) {
        newObject.checkAlreadyExist()
        if (!newObject.validate()) {
            log.error newObject.errors
            log.error newObject.retrieveErrors().toString()
            throw new WrongArgumentException(newObject.retrieveErrors().toString())
        }
        if (!newObject.save(flush: true)) {
            throw new InvalidRequestException(newObject.retrieveErrors().toString())
        }
    }

    /**
     * Delete a domain from database
     */
    def removeDomain(def oldObject) {
        try {
            oldObject.refresh()
            oldObject.delete(flush: true, failOnError: true)
        } catch (Exception e) {
            log.error e.toString()
            throw new InvalidRequestException(e.toString())
        }
    }

    /**
     * Add command info for the new domain concerned by the command
     * @param newObject New domain
     * @param message Message build for the command
     */
    protected def fillDomainWithData(def object, def json) {
        def domain = object.get(json.id)
        domain = object.insertDataIntoDomain(json,domain)
        domain.id = json.id
        return domain
    }

    /**
     * Get the name of the service (project,...)
     */
    public String getServiceName() {
        return GrailsNameUtils.getPropertyName(GrailsNameUtils.getShortName(this.getClass()))
    }

    protected executeCommand(Command c, CytomineDomain domain, def json, Task task = null) {
        //bug, cannot do new XXXCommand(domain:domain, json:...) => new XXXCommand(); c.domain = domain; c.json = ...
        c.domain = domain
        c.json = json
        executeCommand(c,task)
    }

    /**
     * Execute command with JSON data
     */
    protected executeCommand(Command c, Task task = null) {
        log.info "==========> ${this} delete"
        println "2.image=${c.domain}"
        if(c instanceof DeleteCommand) {
            def domainToDelete = c.domain

            //Create a backup (for 'undo' op)
            //We create before for deleteCommand to keep data from HasMany inside json (data will be deleted later)
            def backup = domainToDelete.encodeAsJSON()
            c.backup = backup

            //remove all dependent domains
            def allServiceMethods = this.metaClass.methods*.name
            int numberOfDirectDependence = 0
            def dependencyMethodName = []

            allServiceMethods.each {
                if(it.startsWith("deleteDependent")) {
                    numberOfDirectDependence++
                    dependencyMethodName << "$it"
                }
            }

            dependencyMethodName.unique().eachWithIndex { method, index ->
                log.info "====================> call ${method} with task ${task}"
                taskService.updateTask(task, (int)((double)index/(double)numberOfDirectDependence)*100, "")
                this."$method"(domainToDelete,c.transaction,task)
            }

            task

        }

        initCommandService()
        c.saveOnUndoRedoStack = this.isSaveOnUndoRedoStack() //need to use getter method, to get child value
        c.service = this
        c.serviceName = getServiceName()
        log.info "${getServiceName()} commandService=" + commandService + " c=" + c
        return commandService.processCommand(c)
    }


    private void initCommandService() {
        if (!commandService) {
            commandService =grailsApplication.getMainContext().getBean("commandService")
        }

    }

//    protected def retrieve(def json) {
//        throw new NotYetImplementedException("The retrieve method must be implement in service "+ this.class)
//    }
    /**
     * Retrieve domain thanks to a JSON object
     * @param map MAP with new domain info
     * @return domain retrieve thanks to json
     */
    def retrieve(Map json) {
        println "retrieve json $json"
        CytomineDomain domain = currentDomain().get(json.id)
        if (!domain) {
            throw new ObjectNotFoundException("${currentDomain().class} " + json.id + " not found")
        }
        def container = domain.container()
        if (container) {
            //we only check security if container is defined
            SecurityACL.check(container,READ)
        }
        return domain
    }



    /**
     * Create domain from JSON object
     * @param json JSON with new domain info
     * @return new domain
     */
    CytomineDomain createFromJSON(def json) {
        return currentDomain().insertDataIntoDomain(json)
    }




    /**
     * Create new domain in database
     * @param json JSON data for the new domain
     * @param printMessage Flag to specify if confirmation message must be show in client
     * Usefull when we create a lot of data, just print the root command message
     * @return Response structure (status, object data,...)
     */
    def create(Map json, boolean printMessage) {
        create(currentDomain().insertDataIntoDomain(json), printMessage)
    }

    /**
     * Create new domain in database
     * @param domain Domain to store
     * @param printMessage Flag to specify if confirmation message must be show in client
     * @return Response structure (status, object data,...)
     */
    def create(CytomineDomain domain, boolean printMessage) {
        //Save new object
        beforeAdd(domain)
        saveDomain(domain)

        def response = responseService.createResponseMessage(domain, getStringParamsI18n(domain), printMessage, "Add", domain.getCallBack())
        afterAdd(domain,response)
        //Build response message
        return response
    }


    /**
     * Edit domain from database
     * @param json domain data in json
     * @param printMessage Flag to specify if confirmation message must be show in client
     * @return Response structure (status, object data,...)
     */
    def edit(Map json, boolean printMessage) {
        //Rebuilt previous state of object that was previoulsy edited
        edit(fillDomainWithData(currentDomain().newInstance(), json), printMessage)
    }

    /**
     * Edit domain from database
     * @param domain Domain to update
     * @param printMessage Flag to specify if confirmation message must be show in client
     * @return Response structure (status, object data,...)
     */
    def edit(CytomineDomain domain, boolean printMessage) {
        //Build response message
        def response = responseService.createResponseMessage(domain, getStringParamsI18n(domain), printMessage, "Edit", domain.getCallBack())
        //Save update
        beforeUpdate(domain)
        saveDomain(domain)
        afterUpdate(domain,response)
        return response
    }





    /**
     * Destroy domain from database
     * @param json JSON with domain data (to retrieve it)
     * @param printMessage Flag to specify if confirmation message must be show in client
     * @return Response structure (status, object data,...)
     */
    def destroy(Map json, boolean printMessage) {
        //Get object to delete
        destroy(currentDomain().get(json.id), printMessage)
    }

    /**
     * Destroy domain from database
     * @param domain Domain to remove
     * @param printMessage Flag to specify if confirmation message must be show in client
     * @return Response structure (status, object data,...)
     */
    def destroy(CytomineDomain domain, boolean printMessage) {
        //Build response message
        def response = responseService.createResponseMessage(domain, getStringParamsI18n(domain), printMessage, "Delete", domain.getCallBack())
        beforeDelete(domain)
        //Delete object
        removeDomain(domain)
        afterDelete(domain,response)
        return response
    }


    protected def beforeAdd(def domain) {

    }

    protected def beforeDelete(def domain) {

    }

    protected def beforeUpdate(def domain) {

    }

    protected def afterAdd(def domain, def response) {

    }

    protected def afterDelete(def domain, def response) {

    }

    protected def afterUpdate(def domain, def response) {

    }


    def currentDomain() {
        throw new ServerException("currentDomain must be implemented!")
    }


    def aclUtilService


    def getStringParamsI18n(def domain) {
        throw new ServerException("getStringParamsI18n must be implemented for $this!")
    }

}
