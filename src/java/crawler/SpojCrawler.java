/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crawler;

import hibernate.HibernateUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import model.Problem;
import model.SampleInputOutput;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URL;
import model.Platform;


/**
 *
 * @author h4k3r
 */
public class SpojCrawler implements Crawler {

    static String baseURL = "http://www.spoj.com";

    //method to check sytanx of URL is correct or not
    private static boolean isValidURL(String url) {
        try {
            new URL(url).toURI();
            return true;
        } // If there was an Exception 
        // while creating URL object 
        catch (Exception e) {
            return false;
        }
    }

    //method to check if given url has problem data or not
    private static boolean isProblemUrl(String url) {
        try {
            String pattern = "(.*)(problems)(/[A-Z0-9_]+)(/*)";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(url);

            String pattern1 = "(.*)(/[A-Z0-9_]+)/(problems)(/[A-Z0-9_]+)(/*)";
            Pattern p1 = Pattern.compile(pattern);
            Matcher m1 = p1.matcher(url);

            return m.matches() || m1.matches();

        } catch (Exception e) {

        }
        return false;

    }

    //method to return all problem URLs
    private static HashSet<String> getAllProblemUrls() {
        HashSet<String> urlSet = new HashSet<String>();

        HashSet<String> crawlableLinkSet = new HashSet<String>();
        HashSet<String> problemsUrlSet = new HashSet<String>();
        HashSet<String> tempSet = new HashSet<String>();

        HashMap<String, Integer> visited = new HashMap<String, Integer>();

        crawlableLinkSet.add(baseURL);
        visited.put(baseURL, 0);
        boolean flag = false;

        String pattern = "(/[A-Z0-9]+)/";
        Pattern p = Pattern.compile(pattern);

        while (true) {

            flag = false;
            tempSet.clear();

            for (String url : crawlableLinkSet) {
                //System.out.println("crawling " + url );
                if (visited.get(url) == 0) {

                    flag = true;
                    visited.put(url, 1);
                    //System.out.println("Parsing " + url);
                    Document doc;
                    try {
                        doc = Jsoup.connect(url).timeout(0).get();
                    } catch (IOException ex) {
                        Logger.getLogger(SpojCrawler.class.getName()).log(Level.SEVERE, null, ex);
                        continue;
                    }

                    Elements eles = doc.getElementsByTag("a");

                    for (Element e : eles) {
                        Matcher m = p.matcher(e.attr("href"));
                        String href = e.attr("href");
                        if (href.contains("rss") || href.contains("schools") || href.contains("lang=") || href.contains("status")
                                || href.contains("ranks") || href.contains("rss") || href.contains("http://")
                                || href.contains("tag") || href.contains("info")) {
                            continue;
                        }
                        if (href.contains("problems") || m.matches() || href.contains("contests")) {
                            String urlTemp = baseURL + href;

                            if (isValidURL(urlTemp) && isProblemUrl(urlTemp)) {
                                problemsUrlSet.add(urlTemp);
                            } else if (isValidURL(urlTemp)) {
                                if (!visited.containsKey(urlTemp)) {
                                    visited.put(urlTemp, 0);
                                }
                                System.out.println("adding " + href + "    parent :" + url);
                                tempSet.add(urlTemp);
                            }
                        }
                    }
                }
            }
            //if nothing is left to crawl break the loop
            if (!flag) {
                break;
            }

            //add all retrieved links to linksset
            crawlableLinkSet.clear();
            crawlableLinkSet.addAll(tempSet);
        }

        return problemsUrlSet;
    }

    //method to scrap problem data of given URL
    private static Problem scrapeProblemData(String problemUrl) {
        Problem problem = new Problem();

        String time_limit = "1.0";
        List<String> tags = new ArrayList<String>();

        try {
            Document doc = Jsoup.connect(problemUrl).get();

            Element titleEle = doc.getElementById("problem-name");
            if (titleEle != null) {
                problem.setTitle(titleEle.text());
            }

            //To Scrap problem tags from problem page. //
            Element problem_tags = doc.getElementById("problem-tags");
            if (problem_tags != null) {
                // System.out.println("inside ");
                Elements tagele = problem_tags.getElementsByTag("a");
                if (tagele != null) {
                    for (Element e : tagele) {
                        tags.add(e.text());
                    }
                }
            }

            boolean t_flag = false;

            Elements elements = doc.getAllElements();
            for (Element element : elements) {
                if (element.ownText().contains("Time limit:")) {
                    t_flag = true;
                    continue;
                }

                if (t_flag) {
                    time_limit = element.ownText();

                    time_limit = time_limit.replace("s", "");
                    if (time_limit.contains("-")) {
                        time_limit = time_limit.substring(0, time_limit.indexOf("-"));
                    }
                    break;
                }
            }

            problem.setProblemUrl(problemUrl);
            problem.setPlatform(Platform.Spoj);
            problem.setTimeLimit(Double.parseDouble(time_limit));
            problem.setTags(tags);

            //If Problem link is from practice problems. //
            Element problemBodyEle = doc.getElementById("problem-body");

            //If problem link is from Contest. //
            Element probEle = doc.getElementsByClass("prob").first();

            if (problemBodyEle != null) {
                scrapeProblemDataUtil(problem, problemBodyEle);
            } else {
                scrapeProblemDataUtil(problem, probEle);
            }

        } catch (IOException ex) {
            Logger.getLogger(SpojCrawler.class.getName()).log(Level.SEVERE, "Failed scrap problem data of url:" + problemUrl, ex);
        }

        return problem;
    }

    //helper method to scrap problem data
    private static void scrapeProblemDataUtil(Problem problem, Element element) {

        String input_format = "", output_format = "", problem_statement = "", input = "", output = "", explanations = "", constraints = "";

        //To Scrap Problem Statement,Input,Output.
        Elements eles = element.getAllElements();
        int flag = 0;
        for (Element ele : eles) {

            String owntext = ele.ownText();
            if (flag == 0 && owntext.contains("Input") != true) {
                problem_statement = problem_statement + owntext;
            }
            if (flag == 0 && (owntext.contains("input") == true || owntext.contains("Input") == true || owntext.contains("INPUT") == true)) {
                flag = 1;
                continue;
            }
            if (flag == 1 && !(owntext.contains("output") == true || owntext.contains("Output") == true || owntext.contains("OUTPUT") == true)) {
                input_format = input_format + owntext;
            }

            if (flag == 1 && (owntext.contains("output") == true || owntext.contains("Output") == true || owntext.contains("OUTPUT") == true)) {
                flag = 2;
                continue;
            }
            if (flag == 2 && !(owntext.contains("Example") == true || owntext.contains("Input") == true || owntext.contains("Sample") == true || owntext.contains("example") == true || owntext.contains("Score") == true)) {
                output_format = output_format + owntext;

            }
            if (flag == 2 && owntext.contains("Score") == true) {
                flag = 3;
                continue;
            }
            if (flag == 3 && owntext.contains("Example") == false) {
                explanations = explanations + owntext;
                continue;
            }
            if ((flag == 2 || flag == 3) && (owntext.contains("Example") == true || owntext.contains("Input") == true || owntext.contains("Sample") == true)) {
                break;
            }
        }

        //To Scrap Constraint,Sample input,Sample Output.Score,Warning.//
        String problem_body_text = element.text();

        if (problem_body_text.contains("Constraint")) {
            int constraint_index = problem_body_text.indexOf("Constraint");
            String afterconstraint = problem_body_text.substring(constraint_index + 11);
            if (afterconstraint.contains("Example")) {
                int exampleindex = afterconstraint.indexOf("Example");
                constraints = afterconstraint.substring(0, exampleindex);
            } else if (afterconstraint.contains("Sample")) {
                int sampleindex = afterconstraint.indexOf("Sample");
                constraints = afterconstraint.substring(0, sampleindex);
            } else if (afterconstraint.contains("Input")) {
                int inputindex = afterconstraint.indexOf("Input");
                constraints = afterconstraint.substring(0, inputindex);
            }

        }

        int firstInput = problem_body_text.indexOf("Input");
        String afterFirstInput = problem_body_text.substring(firstInput + 5);
        int secondInput = afterFirstInput.indexOf("Input");
        String afterSecondInput = afterFirstInput.substring(secondInput + 5);
        int asi;
        if (afterSecondInput.contains("Output") == false) {
            asi = afterSecondInput.indexOf("Sample") + 7;
        } else {
            asi = afterSecondInput.indexOf("Output");
        }
        input = afterSecondInput.substring(0, asi);
        if (input.contains("Sample")) {
            input = input.replace("Sample", "");
        }
        if (input.contains(":")) {
            input = input.replace(":", "");
        }
        output = afterSecondInput.substring(asi + 6);
        if (output.contains(":")) {
            output = output.replace(":", "");
        }
        if (output.contains("Tips")) {
            int tipsindex = output.indexOf("Tips");
            explanations = output.substring(tipsindex + 4);
            output = output.substring(0, tipsindex);
        }
        if (output.contains("Scoring")) {
            int scoringindex = output.indexOf("Scoring");
            explanations = output.substring(scoringindex + 7);
            output = output.substring(0, scoringindex);
        }
        if (output.contains("Warning")) {
            int Warningindex = output.indexOf("Warning");
            explanations = explanations + output.substring(Warningindex + 7);
            output = output.substring(0, Warningindex);
        }
        if (output.contains("Explanation")) {
            int explanationindex = output.indexOf("Explanation");
            explanations = explanations + output.substring(explanationindex);
            output = output.substring(0, explanationindex);

        }
        if (output.contains("Note")) {
            int noteindex = output.indexOf("Note");
            explanations = explanations + output.substring(noteindex + 4);
            output = output.substring(0, noteindex);
        }

        problem.setProblemStatement(problem_statement);

        problem.setExplanation(explanations);
        problem.setConstraints(constraints);
        problem.setInputFormat(input_format);
        problem.setOutputFormat(output_format);

        SampleInputOutput sampleInputOutput = new SampleInputOutput(problem, input, output);
        problem.getSampleInputOutputs().add(sampleInputOutput);
    }

    //method to store given problem into database
    private static void storeProblem(Problem problem) {
        Session session = null;
        Transaction transaction = null;
        try {
            //start session
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            //check if problem is already stored in database
            String hql = "FROM Problem p where p.problemUrl = :problem_url";
            Problem oldProblem = (Problem) session.createQuery(hql).setString("problem_url", problem.getProblemUrl()).uniqueResult();
            String task;

            //if problem is present in database
            if (oldProblem != null) {
                //update the old problem
                task = "updated";
                //retrieve id of old problem
                problem.setId(oldProblem.getId());
                session.delete(oldProblem);
                session.flush();
                session.save(problem);
            } else {
                task = "saved";
                System.err.println("saved");
                session.save(problem);
            }
            transaction.commit();
            //log the info to console
            Logger.getLogger(HackerEarthCrawler.class.getName()).log(Level.INFO, "{0} {1}", new Object[]{
                task, problem.getProblemUrl()
            });
        } catch (HibernateException ee) {
            if (transaction != null) {
                transaction.rollback();
            }
            Logger.getLogger(HackerEarthCrawler.class.getName()).log(Level.SEVERE, "Cannot Insert/Update problem into databse: " + problem.getProblemUrl(), ee);
        } finally {
            //close the session
            if (session != null) {
                session.close();
            }
        }
    }

    @Override
    public void crawl() {

        HashSet<String> problemsUrlSet = getAllProblemUrls();
        System.out.println("Total ProblemLinks are: " + problemsUrlSet.size());

        // Scrap process for each problem link //
        for (String problemUrl : problemsUrlSet) {
            Problem problem = scrapeProblemData(problemUrl);
            storeProblem(problem);
        }
    }

}
