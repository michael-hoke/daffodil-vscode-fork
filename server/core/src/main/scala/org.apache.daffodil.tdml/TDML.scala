package org.apache.daffodil.tdml

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.bind.Marshaller
import javax.xml.namespace.QName
import javax.xml.bind.annotation.XmlType

object TDML {
  // Create a ParserTestCaseType object that can be put into a TestSuite
  // These types are generated when JAXB is executed on the TDML schema
  // 
  // The format of the new ParserTestCase is as follows:
  // 
  // <tdml:parserTestCase name="$tdmlName" root="file" model="$schemaPath" description="$tdmlDescription" roundTrip="onePass">
  //   <tdml:document>
  //     <tdml:documentPart type="file">$dataPath</tdml:documentPart>
  //   <tdml:document>
  //   <tdml:infoset>
  //     <tdml:dfdlInfoset type="file">$infosetPath</tdml:dfdlInfoset>
  //   </tdml:infoset>
  // </tdml:parserTestCase>
  // 
  // infosetPath:     Path to the infoset
  // dataPath:        Path to the data file
  // schemaPath:      Path to the DFDL Schema
  // tdmlName:        Name of the DFDL operation
  // tdmlDescription: Description for the DFDL operation
  // 
  // Returns the ParserTestCase object created with the applied paths
  def createTestCase(infosetPath: String, dataPath: String, schemaPath: String, tdmlName: String, tdmlDescription: String): ParserTestCaseType = {
    val factory = new ObjectFactory()

    val dfdlInfoset = factory.createDfdlInfosetType()
    dfdlInfoset.setType("file")
    dfdlInfoset.getContent().add(infosetPath)

    val infoset = factory.createInfosetType()
    infoset.setDfdlInfoset(dfdlInfoset)

    val docPart = factory.createDocumentPartType()
    docPart.setType(DocumentPartTypeEnum.FILE)
    docPart.setValue(dataPath)

    // These lines are necessary because there is no @XmlRootElement annotation on the DocumentPartType class in JAXB
    // Ideally, we would want to have JAXB add the annotation - probably with the bindings.xjb file. The only way I found
    //   that did that required an external plugin just to add the annotation (https://github.com/highsource/jaxb2-annotate-plugin).
    // We are getting the namespace from the JAXB class so that we don't have to hard-code it here
    // Unfortunately, it seems like hard-coding the class name isn't an easy thing to avoid. There is a name in the XmlType
    //   annotation, but it is documentPartType instead of documentPart. We would need to remove the Type from this anyway.
    val tdmlNamespacePrefix = classOf[DocumentPartType].getAnnotation(classOf[XmlType]).namespace()
    val docPartElement = new JAXBElement[DocumentPartType](new QName(tdmlNamespacePrefix, "documentPart"), classOf[DocumentPartType], docPart)

    val doc = factory.createDocumentType()
    doc.getContent().add(docPartElement)

    val testCase = factory.createParserTestCaseType()
    testCase.setName(tdmlName)
    testCase.setRoot("file")
    testCase.setModel(schemaPath)
    testCase.setDescription(tdmlDescription)
    testCase.setRoundTrip(RoundTripType.ONE_PASS)
    testCase.getTutorialOrDocumentOrInfoset().add(doc)
    testCase.getTutorialOrDocumentOrInfoset().add(infoset)

    return testCase
  }

  // Generate a new TDML file.
  // There is a suiteName attribute in the root element (TestSuite) of the document. This is set to $tdmlName
  // Paths given to this function should be relative as it should be expected for the TDML files to be shared on the mailing list
  //
  // infosetPath:     Path to the infoset
  // dataPath:        Path to the data file
  // schemaPath:      Path to the DFDL Schema
  // tdmlName:        Name of the DFDL operation
  // tdmlDescription: Description for the DFDL operation
  // tdmlPath:        Path to the TDML file
  // 
  // There is a suiteName attribute in the root element of the document. This is set to tdmlName
  def generate(infosetPath: String, dataPath: String, schemaPath: String, tdmlName: String, tdmlDescription: String, tdmlPath: String): Unit = {
    val factory = new ObjectFactory()

    val testSuite = factory.createTestSuite()
    testSuite.setSuiteName(tdmlName)
    testSuite.setDefaultRoundTrip(RoundTripType.ONE_PASS)
    testSuite.getTutorialOrParserTestCaseOrDefineSchema().add(createTestCase(infosetPath, dataPath, schemaPath, tdmlName, tdmlDescription))

    val marshaller = JAXBContext.newInstance(classOf[TestSuite]).createMarshaller()
    marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    marshaller.marshal(testSuite, new java.io.File(tdmlPath))
  }

  // Append a new test case to an existing TDML file.
  // Paths given to this function should be relative as it should be expected for the TDML files to be shared on the mailing list
  // 
  // infosetPath:     Path to the infoset
  // dataPath:        Path to the data file
  // schemaPath:      Path to the DFDL Schema
  // tdmlName:        Name of the DFDL operation
  // tdmlDescription: Description for the DFDL operation
  // tdmlPath:        Path to the TDML file
  def append(infosetPath: String, dataPath: String, schemaPath: String, tdmlName: String, tdmlDescription: String, tdmlPath: String): Unit = {

    val testSuite = JAXBContext.newInstance(classOf[TestSuite]).createUnmarshaller().unmarshal(new File(tdmlPath)).asInstanceOf[TestSuite]

    testSuite.getTutorialOrParserTestCaseOrDefineSchema().add(createTestCase(infosetPath, dataPath, schemaPath, tdmlName, tdmlDescription))

    JAXBContext.newInstance(classOf[TestSuite]).createMarshaller().marshal(testSuite, new FileOutputStream(tdmlPath))
  }

  // Find the parameters needed to execute a DFDL parse based on the given TDML Parameters
  // 
  // tdmlName:        Test case name to run
  // tdmlDescription: Description of test case to run
  // tdmlPath:        File path of TDML file to extract test case from
  // 
  // Returns a tuple containing the following (Path to DFDL Schema, Path to Data File)
  // All paths returned could be either relative or absolute - it depends on what exists in the TDML file
  def execute(tdmlName: String, tdmlDescription: String, tdmlPath: String): (String, String) = {
    val testCaseList = JAXBContext.newInstance(classOf[TestSuite]).createUnmarshaller().unmarshal(new File(tdmlPath)).asInstanceOf[TestSuite].getTutorialOrParserTestCaseOrDefineSchema()

    testCaseList.forEach { tc =>
      tc match {
        case ptc: ParserTestCaseType =>
          if (ptc.getName() == tdmlName && ptc.getDescription() == tdmlDescription) {
            ptc.getTutorialOrDocumentOrInfoset().forEach { dis =>
              dis match {
                case doc: DocumentType =>
                  // The right part of the tuple only takes the first DocumentPart inside the Document.
                  // In the case that there are more than one, any extras will be ignored.
                  val schemaPath = Paths.get(ptc.getModel()).toFile().getCanonicalPath()
                  val dataPath = Paths.get(doc.getContent().get(0).asInstanceOf[JAXBElement[DocumentPartType]].getValue().getValue()).toFile().getCanonicalPath()
                  return (schemaPath, dataPath)
              }
            }
          }
      }
    }

    // If there is no test case in the TDML file meeting the name/description criteria, return empty
    ("", "")
  }
}
