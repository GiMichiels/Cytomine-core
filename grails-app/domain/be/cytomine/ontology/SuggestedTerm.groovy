package be.cytomine.ontology

import be.cytomine.SequenceDomain
import be.cytomine.processing.Job
import grails.converters.JSON

class SuggestedTerm extends SequenceDomain implements Serializable{

    Annotation annotation
    Term term
    Double rate
    Job job

    static constraints = {
        annotation nullable : false
        term nullable : false
        rate(min : 0d, max : 1d)
        job nullable : false
    }

    static SuggestedTerm createFromData(jsonSuggestedTerm) {
        def suggestedTerm = new SuggestedTerm()
        getFromData(suggestedTerm,jsonSuggestedTerm)
    }

    static SuggestedTerm getFromData(suggestedTerm,jsonSuggestedTerm) {

        String annotationId = jsonSuggestedTerm.annotation.toString()
        if(!annotationId.equals("null")) {
            suggestedTerm.annotation = Annotation.get(annotationId)
            if(suggestedTerm.annotation==null) throw new IllegalArgumentException("Annotation was not found with id:"+ annotationId)
        }
        else suggestedTerm.annotation = null
        
        String termId = jsonSuggestedTerm.term.toString()
        if(!termId.equals("null")) {
            suggestedTerm.term = Term.get(termId)
            if(suggestedTerm.term==null) throw new IllegalArgumentException("Term was not found with id:"+ termId)
        }
        else suggestedTerm.term = null        
        
        String jobId = jsonSuggestedTerm.job.toString()
        if(!jobId.equals("null")) {
            suggestedTerm.job = Job.get(jobId)
            if(suggestedTerm.job==null) throw new IllegalArgumentException("Job was not found with id:"+ jobId)
        }
        else suggestedTerm.job = null
        
        suggestedTerm.rate = Double.parseDouble(jsonSuggestedTerm.rate+"")
        return suggestedTerm;
    }


    def getIdAnnotation() {
        if(this.annotationId) return this.annotationId
        else return this.annotation?.id
    }

    def getIdTerm() {
        if(this.termId) return this.termId
        else return this.term?.id
    }

    def getIdJob() {
        if(this.jobId) return this.jobId
        else return this.job?.id
    }

    static void registerMarshaller() {
        println "Register custom JSON renderer for " + SuggestedTerm.class
        JSON.registerObjectMarshaller(SuggestedTerm) {
            def returnArray = [:]
            returnArray['id'] = it.id
            returnArray['annotation'] = it.getIdAnnotation()
            returnArray['term'] = it.getIdTerm()
            returnArray['rate'] = it.rate
            returnArray['job'] = it.getIdJob()
            return returnArray
        }
    }
}
