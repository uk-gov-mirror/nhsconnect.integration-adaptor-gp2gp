package uk.nhs.adaptors.gp2gp.ehr.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.stream.Stream;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.ReferralRequest;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import uk.nhs.adaptors.gp2gp.common.service.FhirParseService;
import uk.nhs.adaptors.gp2gp.common.service.RandomIdGeneratorService;
import uk.nhs.adaptors.gp2gp.ehr.exception.EhrMapperException;
import uk.nhs.adaptors.gp2gp.utils.CodeableConceptMapperMockUtil;
import uk.nhs.adaptors.gp2gp.utils.ResourceTestFileUtils;

@ExtendWith(MockitoExtension.class)
public class RequestStatementMapperTest {
    private static final String COMMON_ID_1 = "6D340A1B-BC15-4D4E-93CF-BBCB5B74DF73";
    private static final String PATIENT_2_ID = "4C903809-CADA-442E-8FA5-655E5EFEB1F5";
    private static final String RELATED_PERSON_ID = "f002";
    private static final String ORGANIZATION_ID = "1F90B10F-CF14-4D6F-8C0D-585059DA4EC5";

    private static final String TEST_ID = "394559384658936";
    private static final String TEST_FILE_DIRECTORY = "/ehr/mapper/referral/";
    private static final String INPUT_JSON_BUNDLE =  TEST_FILE_DIRECTORY + "fhir-bundle.json";
    private static final String INPUT_JSON_WITH_NO_OPTIONAL_FIELDS = TEST_FILE_DIRECTORY + "example-referral-request-resource-1.json";
    private static final String INPUT_JSON_WITH_OPTIONAL_FIELDS = TEST_FILE_DIRECTORY + "example-referral-request-resource-2.json";
    private static final String INPUT_JSON_WITH_ONE_REASON_CODE = TEST_FILE_DIRECTORY + "example-referral-request-resource-3.json";
    private static final String INPUT_JSON_WITH_PRACTITIONER_REQUESTER = TEST_FILE_DIRECTORY + "example-referral-request-resource-4.json";
    private static final String INPUT_JSON_WITH_REASON_CODES = TEST_FILE_DIRECTORY + "example-referral-request-resource-5.json";
    private static final String INPUT_JSON_WITH_SERVICES_REQUESTED = TEST_FILE_DIRECTORY + "example-referral-request-resource-6.json";
    private static final String INPUT_JSON_WITH_DEVICE_REQUESTER = TEST_FILE_DIRECTORY + "example-referral-request-resource-7.json";
    private static final String INPUT_JSON_WITH_ORG_REQUESTER = TEST_FILE_DIRECTORY + "example-referral-request-resource-8.json";
    private static final String INPUT_JSON_WITH_PATIENT_REQUESTER = TEST_FILE_DIRECTORY + "example-referral-request-resource-9.json";
    private static final String INPUT_JSON_WITH_RELATION_REQUESTER = TEST_FILE_DIRECTORY + "example-referral-request-resource-10.json";
    private static final String INPUT_JSON_WITH_ONE_PRACTITIONER_RECIPIENT = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-11.json";
    private static final String INPUT_JSON_WITH_MULTIPLE_PRACTITIONER_RECIPIENT = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-12.json";
    private static final String INPUT_JSON_WITH_NOTES = TEST_FILE_DIRECTORY + "example-referral-request-resource-13.json";
    private static final String INPUT_JSON_WITH_INCORRECT_RESOURCE_TYPE_RECIPIENT = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-14.json";
    private static final String INPUT_JSON_WITH_INCORRECT_RESOURCE_TYPE_AUTHOR = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-15.json";
    private static final String INPUT_JSON_WITH_INCORRECT_RESOURCE_TYPE_REQUESTER = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-19.json";
    private static final String INPUT_JSON_WITH_NO_RESOLVED_REFERENCE_REQUESTER = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-16.json";
    private static final String INPUT_JSON_WITH_NO_RESOLVED_REFERENCE_RECIPIENT = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-17.json";
    private static final String INPUT_JSON_WITH_NO_RESOLVED_REFERENCE_NOTE_AUTHOR = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-18.json";
    private static final String INPUT_JSON_WITH_PRACTITIONER_REQUESTER_NO_ONBEHALFOF = TEST_FILE_DIRECTORY
        + "example-referral-request-resource-20.json";
    private static final String OUTPUT_XML_USES_NO_OPTIONAL_FIELDS = TEST_FILE_DIRECTORY + "expected-output-request-statement-1.xml";
    private static final String OUTPUT_XML_USES_OPTIONAL_FIELDS = TEST_FILE_DIRECTORY + "expected-output-request-statement-2.xml";
    private static final String OUTPUT_XML_USES_NESTED_COMPONENT = TEST_FILE_DIRECTORY + "expected-output-request-statement-3.xml";
    private static final String OUTPUT_XML_DOES_NOT_USE_DEFAULT_CODE = TEST_FILE_DIRECTORY + "expected-output-request-statement-4.xml";
    private static final String OUTPUT_XML_WITH_REASON_CODES = TEST_FILE_DIRECTORY + "expected-output-request-statement-5.xml";
    private static final String OUTPUT_XML_WITH_SERVICES_REQUESTED = TEST_FILE_DIRECTORY + "expected-output-request-statement-6.xml";
    private static final String OUTPUT_XML_WITH_DEVICE_REQUESTER = TEST_FILE_DIRECTORY + "expected-output-request-statement-7.xml";
    private static final String OUTPUT_XML_WITH_ORG_REQUESTER = TEST_FILE_DIRECTORY + "expected-output-request-statement-8.xml";
    private static final String OUTPUT_XML_WITH_PATIENT_REQUESTER  = TEST_FILE_DIRECTORY + "expected-output-request-statement-9.xml";
    private static final String OUTPUT_XML_WITH_RELATION_REQUESTER = TEST_FILE_DIRECTORY + "expected-output-request-statement-10.xml";
    private static final String OUTPUT_XML_WITH_RECIPIENTS = TEST_FILE_DIRECTORY + "expected-output-request-statement-11.xml";
    private static final String OUTPUT_XML_WITH_RECIPIENTS_AND_PRACTITIONER = TEST_FILE_DIRECTORY
        + "expected-output-request-statement-12.xml";
    private static final String OUTPUT_XML_WITH_NOTES = TEST_FILE_DIRECTORY + "expected-output-request-statement-13.xml";
    private static final String OUTPUT_XML_WITH_PARTICIPANT = TEST_FILE_DIRECTORY + "expected-output-request-statement-14.xml";

    @Mock
    private RandomIdGeneratorService randomIdGeneratorService;
    @Mock
    private CodeableConceptCdMapper codeableConceptCdMapper;

    private RequestStatementMapper requestStatementMapper;
    private MessageContext messageContext;

    @BeforeEach
    public void setUp() throws IOException {
        when(randomIdGeneratorService.createNewId()).thenReturn(TEST_ID);
        var bundleInput = ResourceTestFileUtils.getFileContent(INPUT_JSON_BUNDLE);
        Bundle bundle = new FhirParseService().parseResource(bundleInput, Bundle.class);

        messageContext = new MessageContext(randomIdGeneratorService);
        messageContext.initialize(bundle);
        var idMapper = messageContext.getIdMapper();
        idMapper.getOrNew(ResourceType.Practitioner, COMMON_ID_1);
        idMapper.getOrNew(ResourceType.Organization, COMMON_ID_1);
        idMapper.getOrNew(ResourceType.Device, COMMON_ID_1);
        idMapper.getOrNew(ResourceType.Patient, COMMON_ID_1);
        idMapper.getOrNew(ResourceType.Patient, PATIENT_2_ID);
        idMapper.getOrNew(ResourceType.RelatedPerson, RELATED_PERSON_ID);
        idMapper.getOrNew(ResourceType.Organization, ORGANIZATION_ID);
        requestStatementMapper = new RequestStatementMapper(messageContext, codeableConceptCdMapper, new ParticipantMapper());
    }

    @AfterEach
    public void tearDown() {
        messageContext.resetMessageContext();
    }

    @ParameterizedTest
    @MethodSource("resourceFileParams")
    public void When_MappingObservationJson_Expect_NarrativeStatementXmlOutput(String inputJson, String outputXml) {
        assertThatInputMapsToExpectedOutput(inputJson, outputXml, false);
    }

    private static Stream<Arguments> resourceFileParams() {
        return Stream.of(
            Arguments.of(INPUT_JSON_WITH_NO_OPTIONAL_FIELDS, OUTPUT_XML_USES_NO_OPTIONAL_FIELDS),
            Arguments.of(INPUT_JSON_WITH_PRACTITIONER_REQUESTER, OUTPUT_XML_WITH_PARTICIPANT),
            Arguments.of(INPUT_JSON_WITH_SERVICES_REQUESTED, OUTPUT_XML_WITH_SERVICES_REQUESTED),
            Arguments.of(INPUT_JSON_WITH_DEVICE_REQUESTER, OUTPUT_XML_WITH_DEVICE_REQUESTER),
            Arguments.of(INPUT_JSON_WITH_ORG_REQUESTER, OUTPUT_XML_WITH_ORG_REQUESTER),
            Arguments.of(INPUT_JSON_WITH_PATIENT_REQUESTER, OUTPUT_XML_WITH_PATIENT_REQUESTER),
            Arguments.of(INPUT_JSON_WITH_RELATION_REQUESTER, OUTPUT_XML_WITH_RELATION_REQUESTER),
            Arguments.of(INPUT_JSON_WITH_ONE_PRACTITIONER_RECIPIENT, OUTPUT_XML_WITH_RECIPIENTS),
            Arguments.of(INPUT_JSON_WITH_MULTIPLE_PRACTITIONER_RECIPIENT, OUTPUT_XML_WITH_RECIPIENTS_AND_PRACTITIONER),
            Arguments.of(INPUT_JSON_WITH_NOTES, OUTPUT_XML_WITH_NOTES),
            Arguments.of(INPUT_JSON_WITH_OPTIONAL_FIELDS, OUTPUT_XML_USES_OPTIONAL_FIELDS),
            Arguments.of(INPUT_JSON_WITH_PRACTITIONER_REQUESTER_NO_ONBEHALFOF, OUTPUT_XML_WITH_PARTICIPANT)
            );
    }

    @ParameterizedTest
    @MethodSource("resourceFileParamsReasonCodes")
    public void When_MappingObservationJsonWithReason_Expect_NarrativeStatementXmlOutput(String inputJson, String outputXml) {
        when(codeableConceptCdMapper.mapCodeableConceptToCd(any(CodeableConcept.class)))
            .thenReturn(CodeableConceptMapperMockUtil.NULL_FLAVOR_CODE);
        assertThatInputMapsToExpectedOutput(inputJson, outputXml, false);
    }

    private static Stream<Arguments> resourceFileParamsReasonCodes() {
        return Stream.of(
            Arguments.of(INPUT_JSON_WITH_ONE_REASON_CODE, OUTPUT_XML_DOES_NOT_USE_DEFAULT_CODE),
            Arguments.of(INPUT_JSON_WITH_REASON_CODES, OUTPUT_XML_WITH_REASON_CODES)
        );
    }

    @Test
    public void When_MappingReferralRequestJsonWithNestedTrue_Expect_RequestStatementXmlOutput() throws IOException {
        String expectedOutputMessage = ResourceTestFileUtils.getFileContent(OUTPUT_XML_USES_NESTED_COMPONENT);
        var jsonInput = ResourceTestFileUtils.getFileContent(INPUT_JSON_WITH_NO_OPTIONAL_FIELDS);
        ReferralRequest parsedReferralRequest = new FhirParseService().parseResource(jsonInput, ReferralRequest.class);

        String outputMessage = requestStatementMapper.mapReferralRequestToRequestStatement(parsedReferralRequest, true);

        assertThat(outputMessage).isEqualTo(expectedOutputMessage);
    }

    @ParameterizedTest
    @MethodSource("resourceFileParamsWithUnexpectedReferences")
    public void When_MappingReferralRequestJsonWithUnexpectedReferences_Expect_Exception(String inputJson, String exceptionMessage)
            throws IOException {
        var jsonInput = ResourceTestFileUtils.getFileContent(inputJson);
        ReferralRequest parsedReferralRequest = new FhirParseService().parseResource(jsonInput, ReferralRequest.class);

        assertThatThrownBy(() -> requestStatementMapper.mapReferralRequestToRequestStatement(parsedReferralRequest, false))
            .isExactlyInstanceOf(EhrMapperException.class)
            .hasMessage(exceptionMessage);
    }

    private static Stream<Arguments> resourceFileParamsWithUnexpectedReferences() {
        return Stream.of(
            Arguments.of(INPUT_JSON_WITH_INCORRECT_RESOURCE_TYPE_REQUESTER, "Requester Reference not of expected Resource Type"),
            Arguments.of(INPUT_JSON_WITH_INCORRECT_RESOURCE_TYPE_RECIPIENT, "Recipient Reference not of expected Resource Type"),
            Arguments.of(INPUT_JSON_WITH_INCORRECT_RESOURCE_TYPE_AUTHOR, "Author Reference not of expected Resource Type"),
            Arguments.of(INPUT_JSON_WITH_NO_RESOLVED_REFERENCE_REQUESTER, "Could not resolve Device Reference"),
            Arguments.of(INPUT_JSON_WITH_NO_RESOLVED_REFERENCE_RECIPIENT, "Could not resolve Organization Reference"),
            Arguments.of(INPUT_JSON_WITH_NO_RESOLVED_REFERENCE_NOTE_AUTHOR, "Could not resolve RelatedPerson Reference")
            );
    }

    @SneakyThrows
    private void assertThatInputMapsToExpectedOutput(String inputJsonResourcePath, String outputXmlResourcePath, boolean isNested) {
        var expected = ResourceTestFileUtils.getFileContent(outputXmlResourcePath);
        var input = ResourceTestFileUtils.getFileContent(inputJsonResourcePath);
        var referralRequest = new FhirParseService().parseResource(input, ReferralRequest.class);

        String outputMessage = requestStatementMapper.mapReferralRequestToRequestStatement(referralRequest, isNested);

        assertThat(outputMessage).isEqualTo(expected);
    }

}
