package org.fuin.cqrs4j.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.fuin.objects4j.common.MarshalInformation;
import org.fuin.utils4j.TestOmitted;

@TestOmitted("This is only a test class")
public class Invoice implements MarshalInformation<Invoice> {

    @JsonProperty("id")
    private String id;

    protected Invoice() {
        super();
    }

    public Invoice(String id) {
        super();
        this.id = id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Invoice other = (Invoice) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    @JsonIgnore
    public Class<Invoice> getDataClass() {
        return Invoice.class;
    }

    @Override
    @JsonIgnore
    public String getDataElement() {
        return Invoice.class.getName();
    }

    @Override
    @JsonIgnore
    public Invoice getData() {
        return this;
    }

    @JsonIgnore
    public String getId() {
        return id;
    }

}
