/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.ws.rs.NameBinding;

/**
 *
 * @author Nikhil
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target(
	{
	    ElementType.TYPE, ElementType.METHOD
	})
public @interface Secured
{

    Role[] roles() default
    {
    };
}
