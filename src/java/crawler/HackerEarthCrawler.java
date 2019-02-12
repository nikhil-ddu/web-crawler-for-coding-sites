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
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import model.Platform;
import model.Problem;
import model.SampleInputOutput;
import model.Tutorial;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 *
 * @author h4k3r
 */
public class HackerEarthCrawler implements Crawler {

    private final static String USER_AGENT = "Mozilla/5.0";
    private final static String BASE_URL = "https://www.hackerearth.com/";

    public HackerEarthCrawler() {
    }

    //method to check if given url has problem data or not
    public static boolean isProblemUrl(String url) {
        //keywords which can't be in problem url
        String[] discard
                = {
                    "/#", "/activity/", "@", "/messages/", "/companies/", "/logout/", "/login/", "/jobs/", "/signup/", "/recruit", "/recruiter/", "/leaderboard/", "/ama/", "/notes/", "/tutorial/", "/customers/", "/users/", "privacy/", "/docs/", "/sql/", "/multiplayer", "/machine-learning/", "/mapreduce/", "/frontend/"
                };
        for (int i = 0; i < discard.length; i++) {
            if (url.contains(discard[i])) {

                return false;
            }
        }
        //if url don't contain 'practice-problem' or 'problem',it's not a problem url

        if ((!url.contains("/practice-problems/") && !url.contains("/problem/")) || !url.startsWith(BASE_URL)) {

            return false;
        } else if ((url.contains("/practice-problems/") && url.endsWith("/practice-problems/")) || url.contains("p_level=#") || url.contains("p_level=")) {

            return false;
        }
        return true;
    }

    //method to check if given url has any problem url or not
    public static boolean isCrawlable(String str) {
        //if these keywords are present in url it cannot have any new problem urls
        String[] discardStrings
                = {
                    "/problem/", "@", "/messages/", "/companies/", "/logout/", "/login/", "/jobs/", "/signup/", "/recruit", "/recruiter/", "/leaderboard/", "/ama/", "/notes/", "/tutorial/", "/customers/", "/users/", "privacy/", "/docs/", "/blog/", "/sprints/", "/innovation/"
                };
        for (String discard : discardStrings) {
            if (str.contains(discard)) {
                return false;
            }
        }
        //if this conditions satisfies it is problem url itself,so not need to be further crawl
        if (str.contains("/practice-problems/") && !str.endsWith("/practice-problems/") && !str.endsWith("p_level=")) {
            return false;
        } else if (str.endsWith("/#")) {
            return false;
        }
        return true;
    }

    
    //method to get all possible urls from HackerEarth platform
    public static HashSet<String> getAllUrls() {
        boolean flag = false;

        //set of Urls that need to be returned
        HashSet<String> urlSet = new HashSet<String>();

        //set of urls which should be crawled
        HashSet<String> crawlableLinkSet = new HashSet<String>();
        HashSet<String> tempSet = new HashSet<String>();

        HashMap<String, Integer> visited = new HashMap<String, Integer>();

        //add base url
        crawlableLinkSet.add(BASE_URL);
        //mark base url as not crawled
        visited.put(BASE_URL, 0);

        try {
            while (true) {
                flag = false;
                tempSet.clear();

                for (String url : crawlableLinkSet) {
                    //check if url is already crawled or not and it has valid domain name
                    if ((visited.get(url) == 0) && (url.startsWith(BASE_URL))) {
                        System.out.println("crawling  " + url);

                        //retriving response of current url as document
                        Document doc = Jsoup.connect(url).get();
                        //Document doc = Jsoup.connect(url).timeout(0).userAgent(USER_AGENT).referrer("http://www.google.com").ignoreHttpErrors(true).get();
                        //retriving all urls from current page
                        Elements links = doc.select("a[href]");

                        //mark url as crawled
                        visited.put(url, 1);

                        //mark flag as url is crawled
                        flag = true;
                        //retrive all urls
                        for (Element link : links) {
                            urlSet.add(link.absUrl("href"));

                            //check if url has valid domain and it has problem urls or not
                            if (link.absUrl("href").contains(BASE_URL) && isCrawlable(link.absUrl("href"))) {
                                //if link is not visited then mark it as uncrawled
                                if (!visited.containsKey(link.absUrl("href"))) {
                                    visited.put(link.absUrl("href"), 0);
                                }
                                //add it in tempsetorary set
                                tempSet.add(link.absUrl("href"));
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

        } catch (IOException ex) {
            Logger.getLogger(HackerEarthCrawler.class.getName()).log(Level.SEVERE, null, ex);
        }

        return urlSet;
    }

    //method to get all problem Urls from given url set
    public static HashSet<String> getProblemUrls(HashSet<String> urlSet) {
        //set of problem urls that need to be returned
        HashSet<String> problemsUrlSet = new HashSet<String>();

        for (String url : urlSet) {
            if (isProblemUrl(url)) {
                problemsUrlSet.add(url);
            }
        }
        return problemsUrlSet;
    }

    //method to get all tutorial Urls from given url set
    public static HashSet<String> getTutorialUrls(HashSet<String> urlSet) {
        HashSet<String> tutorialsUrlSet = new HashSet<String>();
        for (String url : urlSet) {
            if (url.endsWith("/tutorial/")) {
                tutorialsUrlSet.add(url);
            }
        }
        return tutorialsUrlSet;
    }

    
    //method to scrap problem data from given url
    public static Problem scrapeProblemData(String problemUrl) {
        Problem problem = new Problem();

        String problemSIOC = "", problemIOC = "";
        String problemTitle = "", problemStatement = "", problemInput = "", problemOutput = "", problemConstraints = "";
        String sampleInput = "", sampleOutput = "";
        String problemExplanation = "";
        //set default timelimit to 1 second
        double problemTimeLimit = 1.0;
        ArrayList<String> tags = new ArrayList<String>();

        try {

            //get response for given problem url
            Response response = Jsoup.connect(problemUrl).execute();
            Document doc = response.parse();

            //retrieve problem title from page
            Element elementTitle = doc.getElementsByTag("title").first();
            StringTokenizer stTitle = new StringTokenizer(elementTitle.text(), "|");
            problemTitle = stTitle.nextToken().trim();

            Element content = doc.getElementsByClass("starwars-lab").first();
            problemSIOC = content.text();
            Elements e = content.children();

            //to find problem statement
            String breakLoop[]
                    = {
                        "input", "input:", "input :", "input format:", "input format :", "input format", "Input and output", "constraints :", "constraints:", "constraints", "$$Input :$$"
                    };
            boolean flag = false;
            for (Element p : e) {
                String tempStatement = "";
                for (Element pp : p.getAllElements()) {

                    for (String strbreak : breakLoop) {
                        if (StringUtils.equalsIgnoreCase(pp.ownText(), strbreak)) {
                            tempStatement = p.text().substring(0, p.text().toLowerCase().indexOf(strbreak.toLowerCase()));
                            flag = true;
                            break;
                        }
                    }
                }

                if (flag) {
                    problemStatement += tempStatement;
                    //remove extra space at end
                    if (tempStatement.length() == 0) {
                        problemStatement = problemStatement.substring(0, problemStatement.length() - 1);
                    }
                    break;
                }
                problemStatement += p.text() + " ";
            }

            System.out.println("problemSIOC :" + problemSIOC);
            System.out.println("problemStatement :" + problemStatement);

            if (problemStatement.length() <= problemSIOC.length()) {
                //remove problem statement from whole text and remove extra spaces at the beginning and the end
                problemIOC = problemSIOC.substring(problemStatement.length()).trim();
            } else {
                problemIOC = "";
            }

            System.out.println("problemIOC :" + problemIOC);

            //keywords for identifying input
            String decideInput[]
                    = {
                        "Input format :", "Input format:", "Input format", "inputformat:", "inputformat :", "inputformat", "input and output", "input :", "input:", "input"
                    };
            //keywords for identifying output
            String decideOutput[]
                    = {
                        "output format :", "output format:", "Output format", "outputformat:", "outputformat :", "outputformat", "output :", "output:", "output"
                    };
            //keywords for identifying constraint
            String decideConstraint[]
                    = {
                        "constraints:", "constraints :", "constraints", "Constraints :", "constraint:", "constraint :", "constraint", "Contraints :"
                    };

            int posin = 0, posoutput = 0, poscon = 0, idxin, idxout, idxcon, inlen = 0, outlen = 0, conlen = 0;
            boolean flaginput = false, flagoutput = false, flagcon = false;

            //find inputformat position,length of keyword
            for (idxin = 0; idxin < decideInput.length; idxin++) {
                if (StringUtils.containsIgnoreCase(problemIOC, decideInput[idxin])) {

                    posin = problemIOC.toLowerCase().indexOf(decideInput[idxin].toLowerCase());
                    flaginput = true;
                    inlen = decideInput[idxin].length();

                    //decide it is keyowrd for actucal input or it is "sample input"
                    if (StringUtils.containsIgnoreCase(problemIOC, "sample input")) {
                        if (posin > problemIOC.toLowerCase().indexOf("sample input")) {
                            flaginput = false;
                            inlen = 0;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }

            //find outputformat position,length of keyword
            for (idxout = 0; idxout < decideOutput.length; idxout++) {
                if (StringUtils.containsIgnoreCase(problemIOC, decideOutput[idxout])) {
                    posoutput = problemIOC.toLowerCase().indexOf(decideOutput[idxout].toLowerCase());
                    flagoutput = true;
                    outlen = decideOutput[idxout].length();
                    break;
                }
            }

            //find constraint position,length of keyword
            for (idxcon = 0; idxcon < decideConstraint.length; idxcon++) {
                if (StringUtils.containsIgnoreCase(problemIOC, decideConstraint[idxcon])) {
                    poscon = problemIOC.toLowerCase().indexOf(decideConstraint[idxcon].toLowerCase());
                    flagcon = true;
                    conlen = decideConstraint[idxcon].length();
                    break;
                }
            }

            //System.out.println("input " + flaginput + " " + inlen + " " + posin);
            //System.out.println("output " + flagoutput + " " + outlen + " " + posoutput);
            // System.out.println("constraint " + flagcon + " " + conlen + " " + poscon);
            //retrieve problem input and output if present in problem page
            //if input format is present
            if (flaginput) {
                //if input keyword is "input and output" and contraint is present in problem page
                if (idxin == 6 && flagcon) {
                    problemInput = problemIOC.substring(inlen, poscon);
                } //if input keyword is "input and output" and contraint is not present in problem page
                else if (idxin == 6 && !flagcon) {
                    problemInput = problemIOC.substring(inlen);
                } //if output format and constraint is present
                else if (flagoutput && flagcon) {
                    //if constraint is present before input format
                    if (poscon < posin) {
                        problemInput = problemIOC.substring(posin + inlen, posoutput);
                        problemOutput = problemIOC.substring(posoutput + outlen);
                    } //if constraint is present before sample
                    else if (poscon < posoutput) {
                        problemInput = problemIOC.substring(inlen, poscon);
                        problemOutput = problemIOC.substring(posoutput + outlen);
                    } else {
                        problemInput = problemIOC.substring(inlen, posoutput);
                        problemOutput = problemIOC.substring(posoutput + outlen, poscon);
                    }
                } //if constraint is not present
                else if (flagoutput && !flagcon) {
                    problemInput = problemIOC.substring(inlen, posoutput);
                    problemOutput = problemIOC.substring(posoutput + outlen);
                } else if (!flagoutput && flagcon) {
                    if (poscon < posin) {
                        problemInput = problemIOC.substring(posin + inlen);
                    } else {
                        problemInput = problemIOC.substring(poscon + conlen, posin);
                    }
                    problemOutput = "";
                } else {
                    problemInput = problemIOC.substring(inlen);
                    problemOutput = "";
                }
            } //if input format and output format is not present
            else {
                problemInput = "";
                problemOutput = "";
            }

            //if constraint is present
            if (flagcon) {
                //if constraint is present before input format
                if (poscon < posin) {
                    problemConstraints = problemIOC.substring(0, posin);
                } //if constraint is present before output format
                else if (poscon < posoutput) {
                    problemConstraints = problemIOC.substring(poscon + conlen, posoutput);
                } else {
                    problemConstraints = problemIOC.substring(poscon + conlen);
                }
            }

            System.out.println("problemInput :" + problemInput);
            System.out.println("problemOutput :" + problemOutput);
            System.out.println("problemConstraints :" + problemConstraints);

            //retrieve problem tags from problem page
            Element elementtag = doc.getElementsByClass("problem-tags").first().child(1);
            StringTokenizer st = new StringTokenizer(elementtag.text(), ",");
            while (st.hasMoreTokens()) {
                tags.add(st.nextToken().trim());
            }

            //retrieve sample input sample output if present
            Element elementSIO = doc.getElementsByClass("input-output-container").first();
            //if sample input output is present
            if (elementSIO != null) {
                //find position of sample output
                int soutpos = elementSIO.text().indexOf("SAMPLE OUTPUT");
                sampleInput = elementSIO.text().substring(12, soutpos);
                sampleOutput = elementSIO.text().substring(soutpos + 13);
                System.out.println("Sample Input :\n" + sampleInput + "\n\n\n");
                System.out.println("Sample Output :\n" + sampleOutput);
            } else {
                sampleInput = "";
                sampleOutput = "";
            }

            //retrieve problem explanation from problem page if present
            Element elementExplanation = doc.getElementsByClass("standard-margin").first().child(0);
            if (elementExplanation.text().toLowerCase().contains("explanation")) {
                problemExplanation = elementExplanation.nextElementSibling().text();
            }
            System.out.println("Explanation :" + problemExplanation);

            //retrieve timelimit
            Element elementTL = doc.getElementsByClass("problem-guidelines").first().child(0).child(1);
            StringTokenizer stTL = new StringTokenizer(elementTL.ownText(), " ");
            problemTimeLimit = Double.parseDouble(stTL.nextToken());

            System.out.println("problemTimeLimit :" + problemTimeLimit);

            //set all retrieved information to problem class
            problem.setProblemUrl(problemUrl);
            if (problemTitle.length() == 0) {
                problemTitle = null;
            }
            if (problemStatement.length() == 0) {
                problemStatement = null;
            }
            if (problemInput.length() == 0) {
                problemInput = null;
            }
            if (problemOutput.length() == 0) {
                problemOutput = null;
            }
            if (problemExplanation.length() == 0) {
                problemExplanation = null;
            }
            if (problemConstraints.length() == 0) {
                problemConstraints = null;
            }
            problem.setTitle(problemTitle);
            problem.setProblemUrl(problemUrl);
            problem.setProblemStatement(problemStatement);
            problem.setInputFormat(problemInput);
            problem.setOutputFormat(problemOutput);
            problem.setTimeLimit(problemTimeLimit);
            problem.setExplanation(problemExplanation);
            problem.setConstraints(problemConstraints);

            //set sample input output to problem class
            SampleInputOutput sampleInputOutput = new SampleInputOutput(problem, sampleInput, sampleOutput);
            problem.getSampleInputOutputs().add(sampleInputOutput);
            //set platform as hackerearth
            problem.setPlatform(Platform.HackerEarth);
            for (String strtag : tags) {
                problem.getTags().add(strtag);
            }
        } catch (Exception ex) {
            Logger.getLogger(HackerEarthCrawler.class.getName()).log(Level.SEVERE, "Failed scrap problem data of url:" + problemUrl, ex);
        }

        return problem;
    }

    // method to scrap tutorial data from given url
    public static Tutorial scrapeTutorialData(String tutorialUrl) {
        Tutorial tutorial = new Tutorial();
        try {
            Response tutorialres = Jsoup.connect(tutorialUrl).execute();
            Document doc = tutorialres.parse();

            tutorial.setContent(doc.getElementsByClass("tutorial").first().text());

            tutorial.setName(BASE_URL);
            tutorialUrl = tutorialUrl.substring(0, tutorialUrl.length() - 10);
            StringTokenizer tutorialtok = new StringTokenizer(tutorialUrl, "/");

            String tempStr = "";
            while (tutorialtok.hasMoreTokens()) {
                tempStr = tutorialtok.nextToken();
            }
            tutorial.setName(tempStr);
        } catch (IOException ex) {
            Logger.getLogger(HackerEarthCrawler.class.getName()).log(Level.SEVERE, "Failed to scrap tutorial data of url:" + tutorialUrl, ex);
        }
        return tutorial;
    }

    ///methoda to store problem data into database
    public static void storeProblem(Problem problem) {
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

    
    // method to store tutorial data into database
    public static void storeTutorial(Tutorial tutorial) {

        Session session = null;
        Transaction transaction = null;
        try {
            //start session
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            //check if problem is already stored in database
            String hql = "FROM Tutorial p where p.name = :name";
            Tutorial oldProblem = (Tutorial) session.createQuery(hql).setString("name", tutorial.getName()).uniqueResult();
            String task;

            //if problem is present in database
            if (oldProblem != null) {
                //update the old problem
                task = "updated";
                session.delete(oldProblem);
                session.flush();
                session.save(tutorial);
            } else {
                task = "saved";
                session.save(tutorial);
            }

            transaction.commit();
            //log the info to console
            Logger.getLogger(HackerEarthCrawler.class.getName()).log(Level.INFO, "{0} {1}", new Object[]{
                task, tutorial.getName()
            });
        } catch (HibernateException ee) {
            if (transaction != null) {
                transaction.rollback();
            }
            Logger.getLogger(HackerEarthCrawler.class.getName()).log(Level.SEVERE, "Cannot Insert/Update tutorial into databse: " + tutorial.getName(), ee);
        } finally {
            //close the session
            if (session != null) {
                session.close();
            }
        }
    }

    @Override
    public void crawl() {
        HashSet<String> urlSet = getAllUrls();
        HashSet<String> problemsUrlSet = getProblemUrls(urlSet);
        HashSet<String> tutorialsUrlSet = getTutorialUrls(urlSet);

        System.out.println("Total urls " + urlSet.size());
        System.out.println("Total problems " + problemsUrlSet.size());
        System.out.println("Total tutorials " + tutorialsUrlSet.size());

        for (String problemUrl : problemsUrlSet) {

            System.out.println("trying to scrap and store problemUrl :" + problemUrl);
            Problem problem = scrapeProblemData(problemUrl);
            storeProblem(problem);
        }

        //System.out.println("\n\n\n\ntutorial urls\n\n");
        for (String tutorialUrl : tutorialsUrlSet) {

            Tutorial tutorial = scrapeTutorialData(tutorialUrl);
            storeTutorial(tutorial);

        }

    }
}
