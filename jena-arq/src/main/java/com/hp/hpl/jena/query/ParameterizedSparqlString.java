/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.query;

import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.iri.IRI;

import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.NodeFactory;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.shared.PrefixMapping;
import com.hp.hpl.jena.shared.impl.PrefixMappingImpl;
import com.hp.hpl.jena.sparql.ARQException;
import com.hp.hpl.jena.sparql.serializer.SerializationContext;
import com.hp.hpl.jena.sparql.util.FmtUtils;
import com.hp.hpl.jena.sparql.util.NodeFactoryExtra;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateRequest;

/**
 * A Parameterized SPARQL String is a SPARQL query/update into which values may
 * be injected. <h3>Injecting Values</h3>
 * <p>
 * Values may be injected in several ways:
 * </p>
 * <ul>
 * <li>By treating a variable in the SPARQL string as a parameter</li>
 * <li>Using JDBC style positional parameters</li>
 * <li>Appending values directly to the command text being built</li>
 * </ul>
 * <h4>Variable Parameters</h3>
 * <p>
 * Any variable in the command may have a value injected to it, injecting a
 * value replaces all usages of that variable in the command i.e. substitutes
 * the variable for a constant, injection is done by textual substitution.
 * </p> <h4>Positional Parameters</h4>
 * <p>
 * You can use JDBC style positional parameters if you prefer, a JDBC style
 * parameter is a single {@code ?} followed by whitespace or certain punctuation
 * characters (currently {@code ; , .}). Positional parameters have a unique
 * index which reflects the order in which they appear in the string.  Positional
 * parameters use a zero based index.
 * </p>
 * <h4>Buffer Usage</h3> </p> Additionally you may use this purely as a
 * {@link StringBuffer} replacement for creating queries since it provides a
 * large variety of convenience methods for appending things either as-is or as
 * nodes (which causes appropriate formatting to be applied). </p>
 * <h3>Intended Usage</h3>
 * <p>
 * The intended usage of this is where using a {@link QuerySolutionMap} as
 * initial bindings is either inappropriate or not possible e.g.
 * </p>
 * <ul>
 * <li>Generating query/update strings in code without lots of error prone and
 * messy string concatenation</li>
 * <li>Preparing a query/update for remote execution</li>
 * <li>Where you do not want to simply say some variable should have a certain
 * value but rather wish to insert constants into the query/update in place of
 * variables</li>
 * <li>Defending against SPARQL injection when creating a query/update using
 * some external input</li>
 * <li>Provide a more convenient way to prepend common prefixes to your query</li>
 * </ul>
 * <p>
 * This class is useful for preparing both queries and updates hence the generic
 * name as it provides programmatic ways to replace variables in the query with
 * constants and to add prefix and base declarations. A {@link Query} or
 * {@link UpdateRequest} can be created using the {@link #asQuery()} and
 * {@link #asUpdate()} methods assuming the command an instance represents is
 * actually valid as a query/update.
 * </p>
 * <h3>Warnings</h3>
 * <ol>
 * <li>Note that this class does not in any way check that your command is
 * syntactically correct until such time as you try and parse it as a
 * {@link Query} or {@link UpdateRequest}.</li>
 * <li>Also note that injection is done purely based on textual replacement, it
 * does not understand or respect variable scope in any way. For example if your
 * command text contains sub queries you should ensure that variables within the
 * sub query which you don't want replaced have distinct names from those in the
 * outer query you do want replaced (or vice versa)</li>
 * </ol>
 */
public class ParameterizedSparqlString implements PrefixMapping {

    private Model model = ModelFactory.createDefaultModel();

    private StringBuilder cmd = new StringBuilder();
    private String baseUri;
    private Map<String, Node> params = new HashMap<String, Node>();
    private Map<Integer, Node> positionalParams = new HashMap<Integer, Node>();
    private PrefixMapping prefixes;

    /**
     * Creates a new parameterized string
     * 
     * @param command
     *            Raw Command Text
     * @param map
     *            Initial Parameters to inject
     * @param base
     *            Base URI
     * @param prefixes
     *            Prefix Mapping
     */
    public ParameterizedSparqlString(String command, QuerySolutionMap map, String base, PrefixMapping prefixes) {
        if (command != null)
            this.cmd.append(command);
        this.setParams(map);
        this.baseUri = (base != null && !base.equals("") ? base : null);
        this.prefixes = new PrefixMappingImpl();
        if (prefixes != null)
            this.prefixes.setNsPrefixes(prefixes);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param command
     *            Raw Command Text
     * @param map
     *            Initial Parameters to inject
     * @param base
     *            Base URI
     */
    public ParameterizedSparqlString(String command, QuerySolutionMap map, String base) {
        this(command, map, base, null);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param command
     *            Raw Command Text
     * @param map
     *            Initial Parameters to inject
     * @param prefixes
     *            Prefix Mapping
     */
    public ParameterizedSparqlString(String command, QuerySolutionMap map, PrefixMapping prefixes) {
        this(command, map, null, prefixes);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param command
     *            Raw Command Text
     * @param map
     *            Initial Parameters to inject
     */
    public ParameterizedSparqlString(String command, QuerySolutionMap map) {
        this(command, map, null, null);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param command
     *            Raw Command Text
     * @param base
     *            Base URI
     * @param prefixes
     *            Prefix Mapping
     */
    public ParameterizedSparqlString(String command, String base, PrefixMapping prefixes) {
        this(command, null, base, prefixes);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param command
     *            Raw Command Text
     * @param prefixes
     *            Prefix Mapping
     */
    public ParameterizedSparqlString(String command, PrefixMapping prefixes) {
        this(command, null, null, prefixes);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param command
     *            Raw Command Text
     * @param base
     *            Base URI
     */
    public ParameterizedSparqlString(String command, String base) {
        this(command, null, base, null);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param command
     *            Raw Command Text
     */
    public ParameterizedSparqlString(String command) {
        this(command, null, null, null);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param map
     *            Initial Parameters to inject
     * @param prefixes
     *            Prefix Mapping
     */
    public ParameterizedSparqlString(QuerySolutionMap map, PrefixMapping prefixes) {
        this(null, map, null, prefixes);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param map
     *            Initial Parameters to inject
     */
    public ParameterizedSparqlString(QuerySolutionMap map) {
        this(null, map, null, null);
    }

    /**
     * Creates a new parameterized string
     * 
     * @param prefixes
     *            Prefix Mapping
     */
    public ParameterizedSparqlString(PrefixMapping prefixes) {
        this(null, null, null, prefixes);
    }

    /**
     * Creates a new parameterized string with an empty command text
     */
    public ParameterizedSparqlString() {
        this("", null, null, null);
    }

    /**
     * Sets the command text, overwriting any existing command text. If you want
     * to append to the command text use one of the {@link #append(String)},
     * {@link #appendIri(String)}, {@link #appendLiteral(String)} or
     * {@link #appendNode(Node)} methods instead
     * 
     * @param command
     *            Command Text
     */
    public void setCommandText(String command) {
        this.cmd = new StringBuilder();
        this.cmd.append(command);
    }

    /**
     * Appends some text to the existing command text
     * 
     * @param text
     *            Text to append
     */
    public void append(String text) {
        this.cmd.append(text);
    }

    /**
     * Appends a character to the existing command text
     * 
     * @param c
     *            Character to append
     */
    public void append(char c) {
        this.cmd.append(c);
    }

    /**
     * Appends a boolean as-is to the existing command text, to ensure correct
     * formatting when used as a constant consider using the
     * {@link #appendLiteral(boolean)} method
     * 
     * @param b
     *            Boolean to append
     */
    public void append(boolean b) {
        this.cmd.append(b);
    }

    /**
     * Appends a double as-is to the existing command text, to ensure correct
     * formatting when used as a constant consider using the
     * {@link #appendLiteral(double)} method
     * 
     * @param d
     *            Double to append
     */
    public void append(double d) {
        this.cmd.append(d);
    }

    /**
     * Appends a float as-is to the existing command text, to ensure correct
     * formatting when used as a constant consider using the
     * {@link #appendLiteral(float)} method
     * 
     * @param f
     *            Float to append
     */
    public void append(float f) {
        this.cmd.append(f);
    }

    /**
     * Appends an integer as-is to the existing command text, to ensure correct
     * formatting when used as a constant consider using the
     * {@link #appendLiteral(int)} method
     * 
     * @param i
     *            Integer to append
     */
    public void append(int i) {
        this.cmd.append(i);
    }

    /**
     * Appends a long as-is to the existing command text, to ensure correct
     * formatting when used as a constant consider using the
     * {@link #appendLiteral(long)} method
     * 
     * @param l
     *            Long to append
     */
    public void append(long l) {
        this.cmd.append(l);
    }

    /**
     * Appends an object to the existing command text
     * 
     * @param obj
     *            Object to append
     */
    public void append(Object obj) {
        this.cmd.append(obj);
    }

    /**
     * Appends a Node to the command text as a constant using appropriate
     * formatting
     * 
     * @param n
     *            Node to append
     */
    public void appendNode(Node n) {
        SerializationContext context = new SerializationContext(this.prefixes);
        context.setBaseIRI(this.baseUri);
        this.cmd.append(FmtUtils.stringForNode(n, context));
    }

    /**
     * Appends a Node to the command text as a constant using appropriate
     * formatting
     * 
     * @param n
     *            Node to append
     */
    public void appendNode(RDFNode n) {
        this.appendNode(n.asNode());
    }

    /**
     * Appends a URI to the command text as a constant using appropriate
     * formatting
     * 
     * @param uri
     *            URI to append
     */
    public void appendIri(String uri) {
        this.cmd.append(FmtUtils.stringForURI(uri));
    }

    /**
     * Appends an IRI to the command text as a constant using appropriate
     * formatting
     * 
     * @param iri
     *            IRI to append
     */
    public void appendIri(IRI iri) {
        this.appendIri(iri.toString());
    }

    /**
     * Appends a simple literal as a constant using appropriate formatting
     * 
     * @param value
     *            Lexical Value
     */
    public void appendLiteral(String value) {
        this.appendNode(NodeFactoryExtra.createLiteralNode(value, null, null));
    }

    /**
     * Appends a literal with a lexical value and language to the command text
     * as a constant using appropriate formatting
     * 
     * @param value
     *            Lexical Value
     * @param lang
     *            Language
     */
    public void appendLiteral(String value, String lang) {
        this.appendNode(NodeFactoryExtra.createLiteralNode(value, lang, null));
    }

    /**
     * Appends a Typed Literal to the command text as a constant using
     * appropriate formatting
     * 
     * @param value
     *            Lexical Value
     * @param datatype
     *            Datatype
     */
    public void appendLiteral(String value, RDFDatatype datatype) {
        this.appendNode(NodeFactoryExtra.createLiteralNode(value, null, datatype.getURI()));
    }

    /**
     * Appends a boolean to the command text as a constant using appropriate
     * formatting
     * 
     * @param b
     *            Boolean to append
     */
    public void appendLiteral(boolean b) {
        this.appendNode(this.model.createTypedLiteral(b));
    }

    /**
     * Appends an integer to the command text as a constant using appropriate
     * formatting
     * 
     * @param i
     *            Integer to append
     */
    public void appendLiteral(int i) {
        this.appendNode(NodeFactoryExtra.intToNode(i));
    }

    /**
     * Appends a long to the command text as a constant using appropriate
     * formatting
     * 
     * @param l
     *            Long to append
     */
    public void appendLiteral(long l) {
        this.appendNode(NodeFactoryExtra.intToNode(l));
    }

    /**
     * Appends a float to the command text as a constant using appropriate
     * formatting
     * 
     * @param f
     *            Float to append
     */
    public void appendLiteral(float f) {
        this.appendNode(this.model.createTypedLiteral(f));
    }

    /**
     * Appends a double to the command text as a constant using appropriate
     * formatting
     * 
     * @param d
     *            Double to append
     */
    public void appendLiteral(double d) {
        this.appendNode(this.model.createTypedLiteral(d));
    }

    /**
     * Appends a date time to the command text as a constant using appropriate
     * formatting
     * 
     * @param dt
     *            Date Time to append
     */
    public void appendLiteral(Calendar dt) {
        this.appendNode(this.model.createTypedLiteral(dt));
    }

    /**
     * Gets the basic Command Text
     * <p>
     * <strong>Note:</strong> This will not reflect any injected parameters, to
     * see the command with injected parameters invoke the {@link #toString()}
     * method
     * </p>
     * 
     * @return Command Text
     */
    public String getCommandText() {
        return this.cmd.toString();
    }

    /**
     * Sets the Base URI which will be prepended to the query/update
     * 
     * @param base
     *            Base URI
     */
    public void setBaseUri(String base) {
        this.baseUri = base;
    }

    /**
     * Gets the Base URI which will be prepended to a query
     * 
     * @return Base URI
     */
    public String getBaseUri() {
        return this.baseUri;
    }
    
    /**
     * Helper method which does the validation of the parameters
     * @param n Node
     */
    protected void validateParameterValue(Node n) {
        if (n.isURI()) {
            if (n.getURI().contains(">")) throw new ARQException("Value for the parameter attempts SQL injection");
        }
    }

    /**
     * Sets the Parameters
     * 
     * @param map
     *            Parameters
     */
    public void setParams(QuerySolutionMap map) {
        if (map != null) {
            Iterator<String> iter = map.varNames();
            while (iter.hasNext()) {
                String var = iter.next();
                this.setParam(var, map.get(var).asNode());
            }
        }
    }

    /**
     * Sets a Positional Parameter
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(int)} for the given variable
     * </p>
     * 
     * @param index
     *            Positional Index
     * @param n
     *            Node
     */
    public void setParam(int index, Node n) {
        if (index < 0)
            throw new IndexOutOfBoundsException();
        if (n != null) {
            this.validateParameterValue(n);
            this.positionalParams.put(index, n);
        } else {
            this.positionalParams.remove(index);
        }
    }

    /**
     * Sets a variable parameter
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param n
     *            Value
     * 
     */
    public void setParam(String var, Node n) {
        if (var == null)
            throw new IllegalArgumentException("var cannot be null");
        if (var.startsWith("?") || var.startsWith("$"))
            var = var.substring(1);
        if (n != null) {
            this.validateParameterValue(n);
            this.params.put(var, n);
        } else {
            this.params.remove(var);
        }
    }

    /**
     * Sets a positional parameter
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param index
     *            Positional Index
     * @param n
     *            Node
     */
    public void setParam(int index, RDFNode n) {
        this.setParam(index, n.asNode());
    }

    /**
     * Sets a variable parameter
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param n
     *            Value
     */
    public void setParam(String var, RDFNode n) {
        this.setParam(var, n.asNode());
    }

    /**
     * Sets a positional parameter to an IRI
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(int)} for the given index
     * </p>
     * 
     * @param index
     *            Positional Index
     * @param iri
     *            IRI
     */
    public void setIri(int index, String iri) {
        this.setParam(index, NodeFactory.createURI(iri));
    }

    /**
     * Sets a variable parameter to an IRI
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param iri
     *            IRI
     */
    public void setIri(String var, String iri) {
        this.setParam(var, NodeFactory.createURI(iri));
    }

    /**
     * Sets a positional parameter to an IRI
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(int)} for the given index
     * </p>
     * 
     * @param index
     *            Positional Index
     * @param iri
     *            IRI
     */
    public void setIri(int index, IRI iri) {
        this.setIri(index, iri.toString());
    }

    /**
     * Sets a variable parameter to an IRI
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param iri
     *            IRI
     */
    public void setIri(String var, IRI iri) {
        this.setIri(var, iri.toString());
    }

    /**
     * Sets a positional parameter to an IRI
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(int)} for the given index
     * </p>
     * 
     * @param index
     *            Positional Index
     * @param url
     *            URL
     */
    public void setIri(int index, URL url) {
        this.setIri(index, url.toString());
    }

    /**
     * Sets a variable parameter to an IRI
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param url
     *            URL used as IRI
     * 
     */
    public void setIri(String var, URL url) {
        this.setIri(var, url.toString());
    }

    /**
     * Sets a positional parameter to a Literal
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(int)} for the given index
     * </p>
     * 
     * @param index
     *            Positional Index
     * @param lit
     *            Value
     * 
     */
    public void setLiteral(int index, Literal lit) {
        this.setParam(index, lit.asNode());
    }

    /**
     * Sets a variable parameter to a Literal
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param lit
     *            Value
     * 
     */
    public void setLiteral(String var, Literal lit) {
        this.setParam(var, lit.asNode());
    }

    /**
     * Sets a positional parameter to a literal
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(int)} for the given index
     * </p>
     * 
     * @param index
     *            Positional Index
     * @param value
     *            Lexical Value
     * 
     */
    public void setLiteral(int index, String value) {
        this.setParam(index, NodeFactoryExtra.createLiteralNode(value, null, null));
    }

    /**
     * Sets a variable parameter to a literal
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param value
     *            Lexical Value
     * 
     */
    public void setLiteral(String var, String value) {
        this.setParam(var, NodeFactoryExtra.createLiteralNode(value, null, null));
    }

    /**
     * Sets a positional parameter to a literal with a language
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(int)} for the given index
     * </p>
     * 
     * @param index
     *            Positional index
     * @param value
     *            Lexical Value
     * @param lang
     *            Language
     * 
     */
    public void setLiteral(int index, String value, String lang) {
        this.setParam(index, NodeFactoryExtra.createLiteralNode(value, lang, null));
    }

    /**
     * Sets a variable parameter to a literal with a language
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param value
     *            Lexical Value
     * @param lang
     *            Language
     * 
     */
    public void setLiteral(String var, String value, String lang) {
        this.setParam(var, NodeFactoryExtra.createLiteralNode(value, lang, null));
    }

    /**
     * Sets a positional arameter to a typed literal
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(int)} for the given index
     * </p>
     * 
     * @param index
     *            Positional Index
     * @param value
     *            Lexical Value
     * @param datatype
     *            Datatype
     * 
     */
    public void setLiteral(int index, String value, RDFDatatype datatype) {
        this.setParam(index, this.model.createTypedLiteral(value, datatype));
    }

    /**
     * Sets a variable parameter to a typed literal
     * <p>
     * Setting a parameter to null is equivalent to calling
     * {@link #clearParam(String)} for the given variable
     * </p>
     * 
     * @param var
     *            Variable
     * @param value
     *            Lexical Value
     * @param datatype
     *            Datatype
     * 
     */
    public void setLiteral(String var, String value, RDFDatatype datatype) {
        this.setParam(var, this.model.createTypedLiteral(value, datatype));
    }

    /**
     * Sets a positional parameter to a boolean literal
     * 
     * @param index
     *            Positional Index
     * @param value
     *            boolean
     */
    public void setLiteral(int index, boolean value) {
        this.setParam(index, this.model.createTypedLiteral(value));
    }

    /**
     * Sets a variable parameter to a boolean literal
     * 
     * @param var
     *            Variable
     * @param value
     *            boolean
     */
    public void setLiteral(String var, boolean value) {
        this.setParam(var, this.model.createTypedLiteral(value));
    }

    /**
     * Sets a positional parameter to an integer literal
     * 
     * @param index
     *            Positional Index
     * @param i
     *            Integer Value
     */
    public void setLiteral(int index, int i) {
        this.setParam(index, NodeFactoryExtra.intToNode(i));
    }

    /**
     * Sets a variable parameter to an integer literal
     * 
     * @param var
     *            Variable
     * @param i
     *            Integer Value
     */
    public void setLiteral(String var, int i) {
        this.setParam(var, NodeFactoryExtra.intToNode(i));
    }

    /**
     * Sets a positional parameter to an integer literal
     * 
     * @param index
     *            Positional Index
     * @param l
     *            Integer Value
     */
    public void setLiteral(int index, long l) {
        this.setParam(index, NodeFactoryExtra.intToNode(l));
    }

    /**
     * Sets a variable parameter to an integer literal
     * 
     * @param var
     *            Variable
     * @param l
     *            Integer Value
     */
    public void setLiteral(String var, long l) {
        this.setParam(var, NodeFactoryExtra.intToNode(l));
    }

    /**
     * Sets a positional parameter to a float literal
     * 
     * @param index
     *            Positional Index
     * @param f
     *            Float value
     */
    public void setLiteral(int index, float f) {
        this.setParam(index, NodeFactoryExtra.floatToNode(f));
    }

    /**
     * Sets a variable parameter to a float literal
     * 
     * @param var
     *            Variable
     * @param f
     *            Float value
     */
    public void setLiteral(String var, float f) {
        this.setParam(var, NodeFactoryExtra.floatToNode(f));
    }

    /**
     * Sets a positional parameter to a double literal
     * 
     * @param index
     *            Positional Index
     * @param d
     *            Double value
     */
    public void setLiteral(int index, double d) {
        this.setParam(index, this.model.createTypedLiteral(d));
    }

    /**
     * Sets a variable parameter to a double literal
     * 
     * @param var
     *            Variable
     * @param d
     *            Double value
     */
    public void setLiteral(String var, double d) {
        this.setParam(var, this.model.createTypedLiteral(d));
    }

    /**
     * Sets a positional parameter to a date time literal
     * 
     * @param index
     *            Positional Index
     * @param dt
     *            Date Time value
     */
    public void setLiteral(int index, Calendar dt) {
        this.setParam(index, this.model.createTypedLiteral(dt));
    }

    /**
     * Sets a variable parameter to a date time literal
     * 
     * @param var
     *            Variable
     * @param dt
     *            Date Time value
     */
    public void setLiteral(String var, Calendar dt) {
        this.setParam(var, this.model.createTypedLiteral(dt));
    }

    /**
     * Gets the current value for a variable parameter
     * 
     * @param var
     *            Variable
     * @return Current value or null if not set
     */
    public Node getParam(String var) {
        return this.params.get(var);
    }

    /**
     * Gets the current value for a positional parameter
     * 
     * @param index
     *            Positional Index
     * @return Current value or null if not set
     */
    public Node getParam(int index) {
        return this.positionalParams.get(index);
    }

    /**
     * Gets the variable names which are currently treated as variable
     * parameters (i.e. have values set for them)
     * 
     * @return Iterator of variable names
     */
    public Iterator<String> getVars() {
        return this.params.keySet().iterator();
    }

    /**
     * Clears the value for a variable parameter so the given variable will not
     * have a value injected
     * 
     * @param var
     *            Variable
     */
    public void clearParam(String var) {
        this.params.remove(var);
    }

    /**
     * Clears the value for a positional parameter
     * 
     * @param index
     *            Positional Index
     */
    public void clearParam(int index) {
        this.positionalParams.remove(index);
    }

    /**
     * Clears all values for both variable and positional parameters
     */
    public void clearParams() {
        this.params.clear();
        this.positionalParams.clear();
    }

    /**
     * This method is where the actual work happens, the original command text
     * is always preserved and we just generated a temporary command string by
     * prepending the defined Base URI and namespace prefixes at the start of
     * the command and injecting the set parameters into a copy of that base
     * command string and return the resulting command.
     * <p>
     * This class makes no guarantees about the validity of the returned string
     * for use as a SPARQL Query or Update, for example if a variable parameter
     * was injected which was mentioned in the SELECT variables list you'd have
     * a syntax error when you try to parse the query. If you run into issues
     * like this try using a mixture of variable and positional parameters.
     * </p>
     */
    @Override
    public String toString() {
        String command = this.cmd.toString();
        Pattern p;
        
        // Before we do anything scan for obvious things that can lead to SPARQL injection attacks
        // The text "?var" where ?var is bound to a literal is an injection attack
        for (Entry<String, Node> entry : this.params.entrySet()) {
            p = Pattern.compile("\"[?$]" + entry.getKey() + "\"");
            
            if (p.matcher(command).find() && entry.getValue().isLiteral()) {
                throw new ARQException("Command string is vunerable to injection attack, variable ?" + entry.getKey() + " appears surrounded by quotes and is bound to a literal which provides a SPARQL injection attack vector");
            }
        }

        // Go ahead and inject Variable Parameters
        SerializationContext context = new SerializationContext(this.prefixes);
        context.setBaseIRI(this.baseUri);
        Iterator<String> vars = this.params.keySet().iterator();
        while (vars.hasNext()) {
            String var = vars.next();

            p = Pattern.compile("([?$]" + var + ")([^\\w]|$)");
            command = p.matcher(command).replaceAll(
                    Matcher.quoteReplacement(FmtUtils.stringForNode(this.params.get(var), context)) + "$2");
        }

        // Then inject Positional Parameters
        // To do this we need to find the ? we will replace
        p = Pattern.compile("(\\?)[\\s;,.]");
        int index = -1;
        int adj = 0;
        Matcher matcher = p.matcher(command);
        while (matcher.find()) {
            index++;
            MatchResult posMatch = matcher.toMatchResult();

            Node n = this.positionalParams.get(index);
            if (n == null)
                continue;

            String nodeStr = FmtUtils.stringForNode(n, context);
            command = command.substring(0, posMatch.start() + adj) + nodeStr + command.substring(posMatch.start() + adj + 1);
            // Because we are using a matcher over the string state prior to starting replacements we need to
            // track the offset adjustments to make
            adj += nodeStr.length() - 1;
        }

        // Build the final command string
        StringBuilder finalCmd = new StringBuilder();

        // Add BASE declaration
        if (this.baseUri != null) {
            finalCmd.append("BASE ");
            finalCmd.append(FmtUtils.stringForURI(this.baseUri, null, null));
            finalCmd.append('\n');
        }

        // Then pre-pend prefixes
        Iterator<String> pre = this.prefixes.getNsPrefixMap().keySet().iterator();

        while (pre.hasNext()) {
            String prefix = pre.next();
            finalCmd.append("PREFIX ");
            finalCmd.append(prefix);
            finalCmd.append(": ");
            finalCmd.append(FmtUtils.stringForURI(this.prefixes.getNsPrefixURI(prefix), null, null));
            finalCmd.append('\n');
        }

        finalCmd.append(command);
        return finalCmd.toString();
    }

    /**
     * Attempts to take the command text with parameters injected from the
     * {@link #toString()} method and parse it as a {@link Query}
     * 
     * @return Query if the command text is a valid SPARQL query
     * @exception QueryException
     *                Thrown if the command text does not parse
     */
    public Query asQuery() throws QueryException {
        return QueryFactory.create(this.toString());
    }

    /**
     * Attempts to take the command text with parameters injected from the
     * {@link #toString()} method and parse it as a {@link UpdateRequest}
     * 
     * @return Update if the command text is a valid SPARQL Update request
     *         (one/more update commands)
     */
    public UpdateRequest asUpdate() {
        return UpdateFactory.create(this.toString());
    }

    /**
     * Makes a full copy of this parameterized string
     * 
     * @return Copy of the string
     */
    public ParameterizedSparqlString copy() {
        return this.copy(true, true, true);
    }

    /**
     * Makes a copy of the command text, base URI and prefix mapping and
     * optionally copies parameter values
     * 
     * @param copyParams
     *            Whether to copy parameters
     * @return Copy of the string
     */
    public ParameterizedSparqlString copy(boolean copyParams) {
        return this.copy(copyParams, true, true);
    }

    /**
     * Makes a copy of the command text and optionally copies other aspects
     * 
     * @param copyParams
     *            Whether to copy parameters
     * @param copyBase
     *            Whether to copy the Base URI
     * @param copyPrefixes
     *            Whether to copy the prefix mappings
     * @return Copy of the string
     */
    public ParameterizedSparqlString copy(boolean copyParams, boolean copyBase, boolean copyPrefixes) {
        ParameterizedSparqlString copy = new ParameterizedSparqlString(this.cmd.toString(), null,
                (copyBase ? this.baseUri : null), (copyPrefixes ? this.prefixes : null));
        if (copyParams) {
            Iterator<String> vars = this.getVars();
            while (vars.hasNext()) {
                String var = vars.next();
                copy.setParam(var, this.getParam(var));
            }
            for (Entry<Integer, Node> entry : this.positionalParams.entrySet())
            {
                copy.setParam(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    @Override
    public PrefixMapping setNsPrefix(String prefix, String uri) {
        return this.prefixes.setNsPrefix(prefix, uri);
    }

    @Override
    public PrefixMapping removeNsPrefix(String prefix) {
        return this.prefixes.removeNsPrefix(prefix);
    }

    @Override
    public PrefixMapping setNsPrefixes(PrefixMapping other) {
        return this.prefixes.setNsPrefixes(other);
    }

    @Override
    public PrefixMapping setNsPrefixes(Map<String, String> map) {
        return this.prefixes.setNsPrefixes(map);
    }

    @Override
    public PrefixMapping withDefaultMappings(PrefixMapping map) {
        return this.prefixes.withDefaultMappings(map);
    }

    @Override
    public String getNsPrefixURI(String prefix) {
        return this.prefixes.getNsPrefixURI(prefix);
    }

    @Override
    public String getNsURIPrefix(String uri) {
        return this.prefixes.getNsURIPrefix(uri);
    }

    @Override
    public Map<String, String> getNsPrefixMap() {
        return this.prefixes.getNsPrefixMap();
    }

    @Override
    public String expandPrefix(String prefixed) {
        return this.prefixes.expandPrefix(prefixed);
    }

    @Override
    public String shortForm(String uri) {
        return this.prefixes.shortForm(uri);
    }

    @Override
    public String qnameFor(String uri) {
        return this.prefixes.qnameFor(uri);
    }

    @Override
    public PrefixMapping lock() {
        return this.prefixes.lock();
    }

    @Override
    public boolean samePrefixMappingAs(PrefixMapping other) {
        return this.prefixes.samePrefixMappingAs(other);
    }

}
