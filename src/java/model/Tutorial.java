/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author Nikhil
 */
@Entity
@Table(name = "tutorial")
@XmlRootElement
public class Tutorial implements Serializable
{

    @Id
    private String name;

    private String content;

    @Transient
    private List<Link> links;

    public Tutorial()
    {
	links = new ArrayList<>();
    }

    public Tutorial(String name, String content)
    {
	this();
	this.name = name;
	this.content = content;

    }

    @Override
    public String toString()
    {
	return "Tutorial{" + "name=" + getName() + ", content=" + getContent() + '}';
    }

    public String getName()
    {
	return name;
    }

    public void setName(String name)
    {
	this.name = name;
    }

    public String getContent()
    {
	return content;
    }

    public void setContent(String content)
    {
	this.content = content;
    }

    public void addLink(Link link)
    {
	links.add(link);
    }

    public List<Link> getLinks()
    {
	return links;
    }

    public void setLinks(List<Link> links)
    {
	this.links = links;
    }
}
